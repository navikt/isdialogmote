package no.nav.syfo.dialogmote.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.domain.toDialogmoteDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiBasepath = "/api/v1/dialogmote"
const val dialogmoteApiPersonIdentUrlPath = "/personident"
const val dialogmoteApiPlanlagtMoteParam = "planlagtmoteuuid"

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
                            personIdentNumber = personIdentNumber
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
        post("/{$dialogmoteApiPlanlagtMoteParam}") {
            try {
                val callId = getCallId()

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val planlagtMoteUUID = UUID.fromString(call.parameters[dialogmoteApiPlanlagtMoteParam])
                val planlagtMote = dialogmoteService.planlagtMote(
                    planlagtMoteUUID = planlagtMoteUUID,
                    token = token,
                    callId = callId
                )

                if (planlagtMote != null && dialogmoteTilgangService.hasAccessToPlanlagtDialogmoteInnkalling(PersonIdentNumber(planlagtMote.fnr), token, callId)) {
                    val created = dialogmoteService.createMoteinnkalling(
                        planlagtMote = planlagtMote,
                        token = token,
                        callId = callId,
                    )
                    if (created) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create Dialogmoteinnkalling for PlanlagtMoteUuid")
                    }
                } else {
                    val accessDeniedMessage = "Denied Veileder access to creating Dialogmote for PlanlagtMoteUuidt"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not create Dialogmote for PlanlagtMoteUuid"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
