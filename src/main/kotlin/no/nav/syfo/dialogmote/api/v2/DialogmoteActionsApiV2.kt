package no.nav.syfo.dialogmote.api.v2

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiMoteParam = "moteuuid"
const val dialogmoteApiMoteAvlysPath = "/avlys"
const val dialogmoteApiMoteMellomlagrePath = "/mellomlagre"
const val dialogmoteApiMoteFerdigstillPath = "/ferdigstill"
const val dialogmoteApiMoteEndreFerdigstiltPath = "/endreferdigstilt"
const val dialogmoteApiMoteTidStedPath = "/tidsted"
const val dialogmoteActionsApiOvertaPath = "/overta"
const val dialogmoteTildelPath = "/tildel"

fun Route.registerDialogmoteActionsApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route(dialogmoteApiV2Basepath) {
        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteAvlysPath") {
            val callId = getCallId()

            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])
            val avlysDialogmoteDto = call.receive<AvlysDialogmoteDTO>()

            val dialogmote = dialogmoteService.getDialogmote(moteUUID)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = dialogmote.arbeidstaker.personIdent,
                action = "Avlys Dialogmote for moteUUID"
            ) {
                dialogmoteService.avlysMoteinnkalling(
                    callId = callId,
                    dialogmote = dialogmote,
                    avlysDialogmote = avlysDialogmoteDto,
                    token = token,
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteMellomlagrePath") {
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])
            val newReferat = call.receive<NewReferatDTO>()

            val dialogmote = dialogmoteService.getDialogmote(moteUUID)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = dialogmote.arbeidstaker.personIdent,
                action = "Mellomlagre Dialogmote for moteUUID"
            ) {
                dialogmoteService.mellomlagreReferat(
                    dialogmote = dialogmote,
                    opprettetAv = getNAVIdentFromToken(token),
                    referat = newReferat,
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteFerdigstillPath") {
            val callId = getCallId()

            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])
            val newReferat = call.receive<NewReferatDTO>()

            val dialogmote = dialogmoteService.getDialogmote(moteUUID)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = dialogmote.arbeidstaker.personIdent,
                action = "Ferdigstill Dialogmote for moteUUID"
            ) {
                dialogmoteService.ferdigstillMote(
                    callId = callId,
                    dialogmote = dialogmote,
                    opprettetAv = getNAVIdentFromToken(token),
                    referat = newReferat,
                    token = token,
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteEndreFerdigstiltPath") {
            val callId = getCallId()

            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])
            val newReferat = call.receive<NewReferatDTO>()

            val dialogmote = dialogmoteService.getDialogmote(moteUUID)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = dialogmote.arbeidstaker.personIdent,
                action = "Endre Ferdigstilt Dialogmote for moteUUID"
            ) {
                dialogmoteService.endreFerdigstiltReferat(
                    callId = callId,
                    dialogmote = dialogmote,
                    opprettetAv = getNAVIdentFromToken(token),
                    referat = newReferat,
                    token = token,
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{$dialogmoteApiMoteParam}$dialogmoteApiMoteTidStedPath") {
            val callId = getCallId()

            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val moteUUID = UUID.fromString(call.parameters[dialogmoteApiMoteParam])

            val endreDialogmoteTidSted = call.receive<EndreTidStedDialogmoteDTO>()

            val dialogmote = dialogmoteService.getDialogmote(moteUUID)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = dialogmote.arbeidstaker.personIdent,
                action = "Create NewDialogmoteTidSted for moteUUID"
            ) {
                dialogmoteService.nyttMoteinnkallingTidSted(
                    callId = callId,
                    dialogmote = dialogmote,
                    endreDialogmoteTidSted = endreDialogmoteTidSted,
                    token = token,
                )
                call.respond(HttpStatusCode.OK)
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

                val dialogmoter = dialogmoteUuids.map { dialogmoteService.getDialogmote(UUID.fromString(it)) }
                if (dialogmoteTilgangService.hasAccessToAllDialogmotePersons(
                        personIdentList = dialogmoter.map { it.arbeidstaker.personIdent },
                        token,
                        callId
                    )
                ) {
                    dialogmoteService.tildelMoter(getNAVIdentFromToken(token), dialogmoter)
                    call.respond(HttpStatusCode.OK)
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

        post(dialogmoteTildelPath) {
            val callId = getCallId()
            try {
                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")
                val (veilederIdent, dialogmoteUuids) = call.receive<TildelDialogmoterDTO>()
                if (dialogmoteUuids.isEmpty()) {
                    throw IllegalArgumentException("No dialogmoteUuids supplied")
                }

                val dialogmoter = dialogmoteUuids.map { dialogmoteService.getDialogmote(it) }

                if (dialogmoteTilgangService.hasAccessToAllDialogmotePersons(
                        personIdentList = dialogmoter.map { it.arbeidstaker.personIdent },
                        token,
                        callId
                    )
                ) {
                    dialogmoteService.tildelMoter(veilederIdent, dialogmoter)
                    call.respond(HttpStatusCode.OK)
                } else {
                    val accessDeniedMessage = "Denied veileder access to dialogmøter for person with PersonIdent"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not tildele dialogmøter"
                log.error("$illegalArgumentMessage: {}, {}", e.message, callId)
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
