package no.nav.syfo.dialogmote

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getPersonIdentHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiBasepath = "/api/v1/dialogmote"
const val dialogmoteApiPersonIdentUrlPath = "/personident"

fun Route.registerDialogmoteApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient
) {
    route(dialogmoteApiBasepath) {
        get(dialogmoteApiPersonIdentUrlPath) {
            try {
                val callId = getCallId()

                val personIdentNumber = getPersonIdentHeader()?.let { personIdent ->
                    PersonIdentNumber(personIdent)
                } ?: throw IllegalArgumentException("No PersonIdent supplied")

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                when (veilederTilgangskontrollClient.hasAccess(personIdentNumber, token, callId)) {
                    true -> {
                        val dialogmoteList = emptyList<Any>()
                        call.respond(dialogmoteList)
                    }
                    else -> {
                        val accessDeniedMessage = "Denied Veileder access to Dialogmoter for Person with PersonIdent"
                        log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                        call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                    }
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve PersonOppgaveList for PersonIdent"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
