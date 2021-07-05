package no.nav.syfo.dialogmote.api.v2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.api.domain.OvertaDialogmoterDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteActionsApiOvertaPath = "/overta"

fun Route.registerDialogmoteActionsApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route(dialogmoteApiV2Basepath) {
        post(dialogmoteActionsApiOvertaPath) {
            val callId = getCallId()
            try {
                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")
                val (dialogmoteUuids) = call.receive<OvertaDialogmoterDTO>()
                if (dialogmoteUuids.isEmpty()) {
                    throw IllegalArgumentException("No dialogmoteUuids supplied")
                }

                val dialogmoter = dialogmoteUuids.map { dialogmoteService.getDialogmote(UUID.fromString(it)) }
                if (dialogmoteTilgangService.hasAccessToAllDialogmotePersonsWithObo(
                        personIdentNumberList = dialogmoter.map { it.arbeidstaker.personIdent },
                        token,
                        callId
                    )
                ) {
                    val success = dialogmoteService.overtaMoter(getNAVIdentFromToken(token), dialogmoter)
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to Overta Dialogmøter")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to Dialogmøter for Person with PersonIdent"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not Overta Dialogmøter"
                log.error("$illegalArgumentMessage: {}, {}", e.message, callId)
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
