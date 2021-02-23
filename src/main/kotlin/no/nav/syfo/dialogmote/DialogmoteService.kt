package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val moteplanleggerClient: MoteplanleggerClient,
    private val narmesteLederClient: NarmesteLederClient,
) {
    fun getDialogmote(
        moteUUID: UUID
    ): Dialogmote {
        return database.getDialogmote(moteUUID).first().let { pDialogmote ->
            val motedeltakerArbeidstaker = getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
            val motedeltakerArbeidsgiver = getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
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
            val motedeltakerArbeidstaker = getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
            val motedeltakerArbeidsgiver = getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
            val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
            pDialogmote.toDialogmote(
                dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
                dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
                dialogmoteTidStedList = dialogmoteTidStedList,
            )
        }
    }

    fun getDialogmoteDeltakerArbeidstaker(
        moteId: Int,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(moteId)
            .first()
        val motedeltakerArbeidstakerVarselList = getDialogmoteDeltakerArbeidstakerVarselList(
            pMotedeltakerArbeidstaker.id
        )
        return pMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(motedeltakerArbeidstakerVarselList)
    }

    fun getDialogmoteDeltakerArbeidstakerVarselList(
        motedeltakerArbeidstakerId: Int,
    ): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarsel(motedeltakerArbeidstakerId).map {
            it.toDialogmotedeltakerArbeidstaker()
        }
    }

    fun getDialogmoteDeltakerArbeidsgiver(
        moteId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        return database.getMoteDeltakerArbeidsgiver(moteId)
            .first()
            .toDialogmotedeltakerArbeidsgiver()
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
            val newDialogmote = planlagtMote.toNewDialogmote(
                requestByNAVIdent = getNAVIdentFromToken(token)
            )
            val createdDialogmoteIdentifiers = database.createDialogmote(newDialogmote)

            arbeidstakerVarselService.sendVarsel(
                createdAt = LocalDateTime.now(),
                personIdent = newDialogmote.arbeidstaker.personIdent,
                type = MotedeltakerVarselType.INNKALT,
                varselUuid = createdDialogmoteIdentifiers.motedeltakerArbeidstakerVarselIdPair.second,
            )

            // TODO: Implement DialogmoteInnkalling-Varsel to Arbeidsgiver

            val planlagtMoteBekreftet = moteplanleggerClient.bekreftPlanlagtMote(
                planlagtMoteUUID = newDialogmote.planlagtMoteUuid,
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

    fun avlysMoteinnkalling(
        dialogmote: Dialogmote,
        opprettetAv: String,
    ): Boolean {
        val isDialogmoteTidPassed = dialogmote.tidStedList.latest()?.passed()
            ?: throw RuntimeException("Failed to Avlys Dialogmote: No TidSted found")
        database.updateMoteStatus(
            moteId = dialogmote.id,
            moteStatus = DialogmoteStatus.AVLYST,
            opprettetAv = opprettetAv,
        )
        if (isDialogmoteTidPassed) {
            // TODO: Implement DialogmoteInnkalling-Varsel to Arbeidsgiver/Arbeidstaker
        }
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }

    fun nyttMoteinnkallingTidSted(
        dialogmote: Dialogmote,
        newDialogmoteTidSted: NewDialogmoteTidSted,
        opprettetAv: String,
    ): Boolean {
        database.updateMoteTidSted(
            moteId = dialogmote.id,
            newDialogmoteTidSted = newDialogmoteTidSted,
            opprettetAv = opprettetAv,
        )
        // TODO: Implement DialogmoteInnkalling-Varsel to Arbeidsgiver/Arbeidstaker
        return true
    }

    fun ferdigstillMoteinnkalling(
        dialogmote: Dialogmote,
        opprettetAv: String,
    ): Boolean {
        database.updateMoteStatus(
            moteId = dialogmote.id,
            moteStatus = DialogmoteStatus.FERDIGSTILT,
            opprettetAv = opprettetAv,
        )
        // TODO: Implement DialogmoteInnkalling-Referart
        return true
    }
}
