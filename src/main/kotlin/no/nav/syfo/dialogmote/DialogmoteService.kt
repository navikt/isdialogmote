package no.nav.syfo.dialogmote

import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.moteplanlegger.domain.PlanlagtMoteDTO
import no.nav.syfo.client.moteplanlegger.domain.virksomhetsnummer
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.LoggerFactory
import java.util.*

class DialogmoteService(
    private val moteplanleggerClient: MoteplanleggerClient,
    private val narmesteLederClient: NarmesteLederClient,
) {
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
            // TODO: Implement DialogmoteInnkalling from MoteplanleggerUuid
            true
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
