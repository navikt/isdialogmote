package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.toNewDialogmote
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.varsel.narmesteleder.NarmesteLederVarselService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DialogmoteService(
    private val database: DatabaseInterface,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val moteplanleggerClient: MoteplanleggerClient,
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

    suspend fun planlagtMote(
        planlagtMoteUUID: UUID,
        token: String,
        callId: String,
    ): PlanlagtMoteDTO? {
        return moteplanleggerClient.planlagtMote(
            callId = callId,
            token = token,
            planlagtMoteUUID = planlagtMoteUUID,
        )
    }

    suspend fun createMoteinnkalling(
        planlagtMote: PlanlagtMoteDTO,
        callId: String,
        token: String,
    ): Boolean {
        log.info("Received PlanlagtMote with uuid=${planlagtMote.moteUuid}")
        val virksomhetsnummer = planlagtMote.virksomhetsnummer()
            ?: throw IllegalArgumentException("No Virksomhetsnummer was found for PlanlagtMote")
        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = PersonIdentNumber(planlagtMote.fnr),
            virksomhetsnummer = virksomhetsnummer,
            token = token,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.info("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val newDialogmotePlanlagt = planlagtMote.toNewDialogmotePlanlagt(
                requestByNAVIdent = getNAVIdentFromToken(token)
            )

            val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkallingArbeidstaker(
                callId = callId,
                pdfBody = newDialogmotePlanlagt.toPdfModelInnkallingArbeidstaker()
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            database.connection.use { connection ->
                createdDialogmoteIdentifiers = connection.createNewDialogmotePlanlagtWithReferences(
                    commit = false,
                    newDialogmotePlanlagt = newDialogmotePlanlagt,
                )

                val motedeltakerArbeidstakerVarselIdPair = connection.createMotedeltakerVarselArbeidstaker(
                    commit = false,
                    motedeltakerArbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.first,
                    status = "OK",
                    varselType = MotedeltakerVarselType.INNKALT,
                    digitalt = true,
                    pdf = pdfInnkallingArbeidstaker
                )

                arbeidstakerVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    personIdent = newDialogmotePlanlagt.arbeidstaker.personIdent,
                    type = MotedeltakerVarselType.INNKALT,
                    varselUuid = motedeltakerArbeidstakerVarselIdPair.second,
                )

                // TODO: Implement DialogmoteInnkalling-Varsel to Arbeidsgiver

                narmesteLederVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    narmesteLeder = narmesteLeder,
                    varseltype = MotedeltakerVarselType.INNKALT
                )

                connection.commit()
            }

            val planlagtMoteBekreftet = moteplanleggerClient.bekreftPlanlagtMote(
                planlagtMoteUUID = newDialogmotePlanlagt.planlagtMoteUuid,
                token = token,
                callId = callId,
            )
            if (planlagtMoteBekreftet) {
                database.updateMotePlanlagtMoteBekreftet(
                    moteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first
                )
            }
            true
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

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            database.connection.use { connection ->
                createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                    commit = false,
                    newDialogmote = newDialogmote,
                )

                val motedeltakerArbeidstakerVarselIdPair = connection.createMotedeltakerVarselArbeidstaker(
                    commit = false,
                    motedeltakerArbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.first,
                    status = "OK",
                    varselType = MotedeltakerVarselType.INNKALT,
                    digitalt = true,
                    pdf = pdfInnkallingArbeidstaker
                )

                arbeidstakerVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    personIdent = newDialogmote.arbeidstaker.personIdent,
                    type = MotedeltakerVarselType.INNKALT,
                    varselUuid = motedeltakerArbeidstakerVarselIdPair.second,
                )

                narmesteLederVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    narmesteLeder = narmesteLeder,
                    varseltype = MotedeltakerVarselType.INNKALT
                )

                connection.commit()
            }
            true
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        token: String
    ): Boolean {
        val isDialogmoteTidPassed = dialogmote.tidStedList.latest()?.passed()
            ?: throw RuntimeException("Failed to Avlys Dialogmote: No TidSted found")

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysningArbeidstaker(
            callId = callId,
            pdfBody = dialogmote.toPdfModelAvlysningArbeidstaker()
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

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
                    val motedeltakerArbeidstakerVarselIdPair = connection.createMotedeltakerVarselArbeidstaker(
                        commit = false,
                        motedeltakerArbeidstakerId = dialogmote.arbeidstaker.id,
                        status = "OK",
                        varselType = MotedeltakerVarselType.AVLYST,
                        digitalt = true,
                        pdf = pdfAvlysningArbeidstaker,
                    )
                    arbeidstakerVarselService.sendVarsel(
                        createdAt = LocalDateTime.now(),
                        personIdent = dialogmote.arbeidstaker.personIdent,
                        type = MotedeltakerVarselType.AVLYST,
                        varselUuid = motedeltakerArbeidstakerVarselIdPair.second,
                    )
                    narmesteLederVarselService.sendVarsel(
                        createdAt = LocalDateTime.now(),
                        narmesteLeder = narmesteLeder,
                        varseltype = MotedeltakerVarselType.AVLYST
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

                val motedeltakerArbeidstakerVarselIdPair = connection.createMotedeltakerVarselArbeidstaker(
                    commit = false,
                    motedeltakerArbeidstakerId = dialogmote.arbeidstaker.id,
                    status = "OK",
                    varselType = MotedeltakerVarselType.NYTT_TID_STED,
                    digitalt = true,
                    pdf = pdfEndringArbeidstaker
                )
                arbeidstakerVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    personIdent = dialogmote.arbeidstaker.personIdent,
                    type = MotedeltakerVarselType.NYTT_TID_STED,
                    varselUuid = motedeltakerArbeidstakerVarselIdPair.second,
                )
                narmesteLederVarselService.sendVarsel(
                    createdAt = LocalDateTime.now(),
                    narmesteLeder = narmesteLeder,
                    varseltype = MotedeltakerVarselType.NYTT_TID_STED
                )

                connection.commit()
            }
            true
        }
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
