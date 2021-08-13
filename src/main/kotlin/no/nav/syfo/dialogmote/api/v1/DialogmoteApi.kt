package no.nav.syfo.dialogmote.api.v1

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.domain.toDialogmoteDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
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
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
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

                when (dialogmoteTilgangService.hasAccessToDialogmotePerson(personIdentNumber, token, callId)) {
                    true -> {
                        val dialogmoteDTOList = dialogmoteService.getDialogmoteList(
                            personIdentNumber = personIdentNumber,
                        ).map { dialogmote ->
                            dialogmote.toDialogmoteDTO()
                        }
                        call.respond(dialogmoteDTOList)
                    }
                    else -> {
                        val accessDeniedMessage = "Denied Veileder access to Dialogmoter for Person with PersonIdent"
                        log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                        call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                    }
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve DialogmoteList for PersonIdent"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
        post(dialogmoteApiPersonIdentUrlPath) {
            val callId = getCallId()
            try {
                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val newDialogmoteDTO = call.receive<NewDialogmoteDTO>()

                val personidentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)

                if (dialogmoteTilgangService.hasAccessToDialogmotePersonWithDigitalVarselEnabled(
                        personidentNumber,
                        token,
                        callId
                    )
                ) {
                    val created = dialogmoteService.createMoteinnkalling(
                        newDialogmoteDTO = newDialogmoteDTO,
                        token = token,
                        callId = callId,
                    )
                    if (created) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create Dialogmoteinnkalling")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to creating new Dialogmote"
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not create new Dialogmote"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            } catch (e: IllegalStateException) {
                val illegalStateExceptionMessage = "Could not create new Dialogmote"
                log.warn("$illegalStateExceptionMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.Forbidden, e.message ?: illegalStateExceptionMessage)
            }
        }
    }
}
