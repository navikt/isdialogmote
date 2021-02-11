package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.LoggerFactory
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val moteplanleggerClient: MoteplanleggerClient,
    private val narmesteLederClient: NarmesteLederClient,
) {
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
        return database.getMoteDeltakerArbeidstaker(moteId)
            .first()
            .toDialogmotedeltakerArbeidstaker()
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
            val newDialogmote = planlagtMote.toNewDialogmote()
            val createdDialogmoteIdPair = database.createDialogmote(newDialogmote)
            val planlagtMoteBekreftet = moteplanleggerClient.bekreftPlanlagtMote(
                planlagtMoteUUID = newDialogmote.planlagtMoteUuid,
                token = token,
                callId = callId,
            )
            if (planlagtMoteBekreftet) {
                database.updateMotePlanlagtMoteBekreftet(moteId = createdDialogmoteIdPair.first)
            }
            // TODO: Implement DialogmoteInnkalling-Varsel to Arbeidsgiver/Arbeidstaker
            true
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
