package no.nav.syfo.dialogmote.api.v2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiMoteParam = "moteuuid"
const val dialogmoteApiMoteAvlysPath = "/avlys"
const val dialogmoteApiMoteFerdigstillPath = "/ferdigstill"
const val dialogmoteApiMoteTidStedPath = "/tidsted"
const val dialogmoteActionsApiOvertaPath = "/overta"

fun Route.registerDialogmoteActionsApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route(dialogmoteApiV2Basepath) {
        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteAvlysPath") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])
                val avlysDialogmoteDto = call.receive<AvlysDialogmoteDTO>()

                val dialogmote = dialogmoteService.getDialogmote(moteUUID)

                if (dialogmoteTilgangService.hasAccessToDialogmotePersonWithDigitalVarselEnabledWithOBO(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.avlysMoteinnkalling(
                        callId = callId,
                        dialogmote = dialogmote,
                        avlysDialogmote = avlysDialogmoteDto,
                        token = token,
                        onBehalfOf = true,
                    )
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to Avlys Dialogmoteinnkalling")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to Avlys Dialogmote for moteUUID"
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not Avlys Dialogmote for moteUUID"
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
                val newReferat = call.receive<NewReferatDTO>()

                val dialogmote = dialogmoteService.getDialogmote(moteUUID)

                if (dialogmoteTilgangService.hasAccessToDialogmotePersonWithDigitalVarselEnabledWithOBO(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.ferdigstillMote(
                        callId = callId,
                        dialogmote = dialogmote,
                        opprettetAv = getNAVIdentFromToken(token),
                        referat = newReferat,
                        token = token,
                        onBehalfOf = true,
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

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteTidStedPath") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])

                val endreDialogmoteTidSted = call.receive<EndreTidStedDialogmoteDTO>()

                val dialogmote = dialogmoteService.getDialogmote(moteUUID, callId)

                if (dialogmoteTilgangService.hasAccessToDialogmotePersonWithDigitalVarselEnabledWithOBO(dialogmote.arbeidstaker.personIdent, token, callId)) {
                    val success = dialogmoteService.nyttMoteinnkallingTidSted(
                        callId = callId,
                        dialogmote = dialogmote,
                        endreDialogmoteTidSted = endreDialogmoteTidSted,
                        token = token,
                        onBehalfOf = true,
                    )
                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create NewDialogmoteTidSted")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to create NewDialogmoteTidSted for moteUUID"
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not create NewDialogmoteTidSted for moteUUID"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }

        post(dialogmoteActionsApiOvertaPath) {
            val callId = getCallId()
            try {
                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")
                val (dialogmoteUuids) = call.receive<OvertaDialogmoterDTO>()
                if (dialogmoteUuids.isEmpty()) {
                    throw IllegalArgumentException("No dialogmoteUuids supplied")
                }

                val dialogmoter = dialogmoteUuids.map { dialogmoteService.getDialogmote(UUID.fromString(it), callId) }
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
