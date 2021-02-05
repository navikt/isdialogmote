package no.nav.syfo.dialogmote

import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.moteplanlegger.domain.virksomhetsnummer
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.LoggerFactory
import java.util.*

class DialogmoteService(
    private val moteplanleggerClient: MoteplanleggerClient,
    private val narmesteLederClient: NarmesteLederClient,
) {
    suspend fun createMoteinnkalling(
        planlagtMoteUUID: UUID,
        callId: String,
        token: String,
    ): Boolean {
        val planlagtMote = moteplanleggerClient.planlagtMote(
            callId = callId,
            planlagtMoteUUID = planlagtMoteUUID,
            token = token,
        )
        if (planlagtMote == null) {
            log.info("Denied access to Dialogmoter: No PlanlagtMote was found for person")
            return false
        } else {
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
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
