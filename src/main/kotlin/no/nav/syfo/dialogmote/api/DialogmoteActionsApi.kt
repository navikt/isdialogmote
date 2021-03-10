package no.nav.syfo.dialogmote.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.domain.NewDialogmoteTidSted
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiMoteParam = "moteuuid"
const val dialogmoteApiMoteAvlysPath = "/avlys"
const val dialogmoteApiMoteTidStedPath = "/tidsted"
const val dialogmoteApiMoteFerdigstillPath = "/ferdigstill"

fun Route.registerDialogmoteActionsApi(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route(dialogmoteApiBasepath) {
        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteAvlysPath") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])

                val dialogmote = dialogmoteService.getDialogmote(moteUUID)

                if (dialogmoteTilgangService.hasAccessToPlanlagtDialogmoteInnkalling(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.avlysMoteinnkalling(
                        callId = callId,
                        dialogmote = dialogmote,
                        opprettetAv = getNAVIdentFromToken(token)
                    )
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to Avlys Dialogmoteinnkalling")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to Avlys Dialogmote for moteUUID"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not Avlys Dialogmote for moteUUID"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteTidStedPath") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])

                val newDialogmoteTidSted = call.receive<NewDialogmoteTidSted>()

                val dialogmote = dialogmoteService.getDialogmote(moteUUID)

                if (dialogmoteTilgangService.hasAccessToPlanlagtDialogmoteInnkalling(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.nyttMoteinnkallingTidSted(
                        callId = callId,
                        dialogmote = dialogmote,
                        newDialogmoteTidSted = newDialogmoteTidSted,
                        opprettetAv = getNAVIdentFromToken(token)
                    )
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create NewDialogmoteTidSted")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to create NewDialogmoteTidSted for moteUUID"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not create NewDialogmoteTidSted for moteUUID"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteFerdigstillPath") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])

                val dialogmote = dialogmoteService.getDialogmote(moteUUID)

                if (dialogmoteTilgangService.hasAccessToPlanlagtDialogmoteInnkalling(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.ferdigstillMoteinnkalling(
                        dialogmote = dialogmote,
                        opprettetAv = getNAVIdentFromToken(token)
                    )
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to Ferdigstill Dialogmote")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to Ferdigstill Dialogmotefor moteUUID"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not Ferdigstill Dialogmote for moteUUID"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
