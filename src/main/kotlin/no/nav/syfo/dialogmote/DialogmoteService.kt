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
            val referat = getReferat(pDialogmote.uuid)
            pDialogmote.toDialogmote(
                dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
                dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
                dialogmoteTidStedList = dialogmoteTidStedList,
                referat = referat,
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
        val referat = getReferat(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referat = referat,
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

        val anyUnfinishedDialogmote = getDialogmoteList(personIdentNumber).anyUnfinished()
        if (anyUnfinishedDialogmote) {
            throw IllegalStateException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

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
                documentComponentDTOList = newDialogmoteDTO.arbeidstaker.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

            val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkallingArbeidsgiver(
                callId = callId,
                documentComponentDTOList = newDialogmoteDTO.arbeidsgiver.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            database.connection.use { connection ->
                createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                    commit = false,
                    newDialogmote = newDialogmote,
                )
                createAndPublishMoteStatusEndring(
                    connection = connection,
                    dialogmoteIdPair = createdDialogmoteIdentifiers.dialogmoteIdPair,
                    dialogmoteStatus = newDialogmote.status,
                    opprettetAv = newDialogmote.opprettetAv,
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
                    documentArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                    documentArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
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
            documentComponentDTOList = avlysDialogmote.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysningArbeidsgiver(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidsgiver.avlysning,
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
                updateMoteStatus(
                    connection = connection,
                    dialogmote = dialogmote,
                    newDialogmoteStatus = DialogmoteStatus.AVLYST,
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
                        fritekstArbeidstaker = avlysDialogmote.arbeidstaker.begrunnelse,
                        fritekstArbeidsgiver = avlysDialogmote.arbeidsgiver.begrunnelse,
                        documentArbeidstaker = avlysDialogmote.arbeidstaker.avlysning,
                        documentArbeidsgiver = avlysDialogmote.arbeidsgiver.avlysning
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
        endreDialogmoteTidSted: EndreTidStedDialogmoteDTO,
        token: String
    ): Boolean {
        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidStedArbeidstaker(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidstaker.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidStedArbeidsgiver(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidsgiver.endringsdokument
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
                    newDialogmoteTidSted = endreDialogmoteTidSted,
                )
                updateMoteStatus(
                    connection = connection,
                    dialogmote = dialogmote,
                    newDialogmoteStatus = DialogmoteStatus.NYTT_TID_STED,
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
                    varselType = MotedeltakerVarselType.NYTT_TID_STED,
                    fritekstArbeidstaker = endreDialogmoteTidSted.arbeidstaker.begrunnelse,
                    fritekstArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.begrunnelse,
                    documentArbeidstaker = endreDialogmoteTidSted.arbeidstaker.endringsdokument,
                    documentArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.endringsdokument,
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
        documentArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        documentArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
    ) {
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = true,
            pdf = pdfArbeidstaker,
            fritekst = fritekstArbeidstaker,
            document = documentArbeidstaker,
        )
        connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdf = pdfArbeidsgiver,
            fritekst = fritekstArbeidsgiver,
            document = documentArbeidsgiver,
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

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
    ): Boolean {
        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        database.connection.use { connection ->
            updateMoteStatus(
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
            )
            connection.createNewReferat(
                commit = false,
                newReferat = referat.toNewReferat(dialogmote.id),
                pdf = pdfReferat,
            )
            connection.commit()
        }
        return true
    }

    private fun updateMoteStatus(
        connection: Connection,
        dialogmote: Dialogmote,
        newDialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
    ) {
        connection.updateMoteStatus(
            commit = false,
            moteId = dialogmote.id,
            moteStatus = newDialogmoteStatus,
        )
        createAndPublishMoteStatusEndring(
            connection = connection,
            maybeDialogmote = dialogmote,
            dialogmoteIdPair = Pair(dialogmote.id, dialogmote.uuid),
            dialogmoteStatus = newDialogmoteStatus,
            opprettetAv = opprettetAv,
        )
    }

    private fun createAndPublishMoteStatusEndring(
        connection: Connection,
        maybeDialogmote: Dialogmote? = null,
        dialogmoteIdPair: Pair<Int, UUID>,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        publish: Boolean = false,
    ) {
        val (dialogmoteId, dialogmoteUuid) = dialogmoteIdPair
        connection.createMoteStatusEndring(
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            status = dialogmoteStatus,
        )
        if (publish) {
            val dialogmote: Dialogmote = maybeDialogmote ?: getDialogmote(dialogmoteUuid)
            // TODO: Implement publish to Kafka-topic
        }
    }

    fun getReferat(
        moteUUID: UUID
    ): Referat? {
        return database.getReferat(moteUUID).firstOrNull()?.let { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            pReferat.toReferat(andreDeltakere)
        }
    }

    private fun getAndreDeltakere(
        referatId: Int
    ): List<DialogmotedeltakerAnnen> {
        return database.getAndreDeltakereForReferatID(referatId).map { pMotedeltakerAnnen ->
            pMotedeltakerAnnen.toDialogmoteDeltakerAnnen()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
