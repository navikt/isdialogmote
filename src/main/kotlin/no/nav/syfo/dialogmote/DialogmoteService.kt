package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.varsel.narmesteleder.NarmesteLederVarselService
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val pdfGenClient: PdfGenClient,
) {
    fun getDialogmote(
        moteUUID: UUID
    ): Dialogmote {
        return database.getDialogmote(moteUUID).first().let { pDialogmote ->
            val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
            val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
            val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
            pDialogmote.toDialogmote(
                dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
                dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
                dialogmoteTidStedList = dialogmoteTidStedList,
            )
        }
    }

    fun getDialogmoteList(
        personIdentNumber: PersonIdentNumber,
    ): List<Dialogmote> {
        return database.getDialogmoteList(personIdentNumber).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        enhetNr: EnhetNr,
    ): List<Dialogmote> {
        return database.getDialogmoteList(enhetNr).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmoteTidStedList = dialogmoteTidStedList,
        )
    }

    fun getDialogmoteTidStedList(
        moteId: Int
    ): List<DialogmoteTidSted> {
        return database.getTidSted(moteId).map {
            it.toDialogmoteTidSted()
        }
    }

    suspend fun createMoteinnkalling(
        newDialogmoteDTO: NewDialogmoteDTO,
        callId: String,
        token: String,
    ): Boolean {
        val personIdentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)
        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)
        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = personIdentNumber,
            virksomhetsnummer = virksomhetsnummer,
            token = token,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val behandlendeEnhet = behandlendeEnhetClient.getEnhet(
                callId = callId,
                personIdentNumber = personIdentNumber,
                token = token,
            ) ?: throw RuntimeException("Failed to request BehandlendeEnhet of Person")

            val newDialogmote = newDialogmoteDTO.toNewDialogmote(
                requestByNAVIdent = getNAVIdentFromToken(token),
                narmesteLeder = narmesteLeder,
                navEnhet = EnhetNr(behandlendeEnhet.enhetId),
            )

            val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkallingArbeidstaker(
                callId = callId,
                pdfBody = newDialogmote.toPdfModelInnkallingArbeidstaker()
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

            val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkallingArbeidsgiver(
                callId = callId,
                pdfBody = newDialogmote.toPdfModelInnkallingArbeidsgiver()
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            database.connection.use { connection ->
                createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                    commit = false,
                    newDialogmote = newDialogmote,
                )
                createAndSendVarsel(
                    connection = connection,
                    arbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.first,
                    arbeidstakerUuid = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.second,
                    arbeidsgiverId = createdDialogmoteIdentifiers.motedeltakerArbeidsgiverIdList.first,
                    arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfInnkallingArbeidstaker,
                    pdfArbeidsgiver = pdfInnkallingArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.INNKALT,
                    fritekstArbeidstaker = newDialogmote.arbeidstaker.fritekstInnkalling.orEmpty(),
                    fritekstArbeidsgiver = newDialogmote.arbeidsgiver.fritekstInnkalling.orEmpty(),
                    innkallingArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                    innkallingArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
                )

                connection.commit()
            }
            true
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysDialogmote: AvlysDialogmoteDTO,
        token: String
    ): Boolean {
        val isDialogmoteTidPassed = dialogmote.tidStedList.latest()?.passed()
            ?: throw RuntimeException("Failed to Avlys Dialogmote: No TidSted found")

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysningArbeidstaker(
            callId = callId,
            pdfBody = dialogmote.toPdfModelAvlysningArbeidstaker()
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysningArbeidsgiver(
            callId = callId,
            pdfBody = dialogmote.toPdfModelAvlysningArbeidsgiver()
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            token = token,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            database.connection.use { connection ->
                connection.updateMoteStatus(
                    commit = false,
                    moteId = dialogmote.id,
                    moteStatus = DialogmoteStatus.AVLYST,
                    opprettetAv = getNAVIdentFromToken(token),
                )
                if (!isDialogmoteTidPassed) {
                    createAndSendVarsel(
                        connection = connection,
                        arbeidstakerId = dialogmote.arbeidstaker.id,
                        arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                        arbeidsgiverId = dialogmote.arbeidsgiver.id,
                        arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                        pdfArbeidstaker = pdfAvlysningArbeidstaker,
                        pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                        narmesteLeder = narmesteLeder,
                        varselType = MotedeltakerVarselType.AVLYST,
                        fritekstArbeidstaker = avlysDialogmote.fritekst.orEmpty(),
                        fritekstArbeidsgiver = avlysDialogmote.fritekst.orEmpty(),
                    )
                }
                connection.commit()
            }
            true
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        newDialogmoteTidSted: NewDialogmoteTidSted,
        token: String
    ): Boolean {
        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidStedArbeidstaker(
            callId = callId,
            pdfBody = newDialogmoteTidSted.toPdfModelEndringTidStedArbeidstaker()
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidStedArbeidsgiver(
            callId = callId,
            pdfBody = newDialogmoteTidSted.toPdfModelEndringTidStedArbeidsgiver()
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            token = token,
            callId = callId
        )

        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            database.connection.use { connection ->
                connection.updateMoteTidSted(
                    commit = false,
                    moteId = dialogmote.id,
                    newDialogmoteTidSted = newDialogmoteTidSted,
                    opprettetAv = getNAVIdentFromToken(token),
                )
                createAndSendVarsel(
                    connection = connection,
                    arbeidstakerId = dialogmote.arbeidstaker.id,
                    arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                    arbeidsgiverId = dialogmote.arbeidsgiver.id,
                    arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfEndringArbeidstaker,
                    pdfArbeidsgiver = pdfEndringArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.NYTT_TID_STED
                )

                connection.commit()
            }
            true
        }
    }

    private fun createAndSendVarsel(
        connection: Connection,
        arbeidstakerUuid: UUID,
        arbeidstakerId: Int,
        arbeidsgiverId: Int,
        arbeidstakerPersonIdent: PersonIdentNumber,
        pdfArbeidstaker: ByteArray,
        pdfArbeidsgiver: ByteArray,
        narmesteLeder: NarmesteLederDTO,
        varselType: MotedeltakerVarselType,
        fritekstArbeidstaker: String = "",
        fritekstArbeidsgiver: String = "",
        innkallingArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        innkallingArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
    ) {
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = true,
            pdf = pdfArbeidstaker,
            fritekst = fritekstArbeidstaker,
            document = innkallingArbeidstaker,
        )
        connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdf = pdfArbeidsgiver,
            fritekst = fritekstArbeidsgiver,
            document = innkallingArbeidsgiver,
        )
        val now = LocalDateTime.now()
        arbeidstakerVarselService.sendVarsel(
            createdAt = now,
            personIdent = arbeidstakerPersonIdent,
            type = varselType,
            motedeltakerArbeidstakerUuid = arbeidstakerUuid,
            varselUuid = varselArbeidstakerId,
        )
        narmesteLederVarselService.sendVarsel(
            createdAt = now,
            narmesteLeder = narmesteLeder,
            varseltype = varselType
        )
    }

    fun ferdigstillMoteinnkalling(
        dialogmote: Dialogmote,
        opprettetAv: String,
    ): Boolean {
        database.connection.use { connection ->
            connection.updateMoteStatus(
                commit = true,
                moteId = dialogmote.id,
                moteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
            )
        }
        // TODO: Implement DialogmoteInnkalling-Referat
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
