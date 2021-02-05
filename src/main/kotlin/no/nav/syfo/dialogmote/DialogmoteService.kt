package no.nav.syfo.dialogmote

import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import org.slf4j.LoggerFactory
import java.util.*

class DialogmoteService(
    private val moteplanleggerClient: MoteplanleggerClient,
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
        planlagtMote?.let {
            log.info("Received PlanlagtMote with uuid=${planlagtMote.moteUuid}")
            // TODO: Implement DialogmoteInnkalling from MoteplanleggerUuid
            return true
        }
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
