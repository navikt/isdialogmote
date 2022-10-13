package no.nav.syfo.dialogmote.api.v2

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.domain.toDialogmoteDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*

const val dialogmoteApiV2Basepath = "/api/v2/dialogmote"

const val dialogmoteApiPersonIdentUrlPath = "/personident"

const val dialogmoteApiVeilederIdentUrlPath = "/veilederident"

fun Route.registerDialogmoteApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route(dialogmoteApiV2Basepath) {
        get(dialogmoteApiPersonIdentUrlPath) {
            val personIdentNumber = getPersonIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            } ?: throw IllegalArgumentException("No PersonIdent supplied")

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personIdentNumber,
                action = "Read Dialogmoter for Person with PersonIdent"
            ) {
                val dialogmoteDTOList = dialogmoteService.getDialogmoteList(
                    personIdentNumber = personIdentNumber,
                ).map { dialogmote ->
                    dialogmote.toDialogmoteDTO()
                }
                call.respond(dialogmoteDTOList)
            }
        }
        get(dialogmoteApiVeilederIdentUrlPath) {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val dialogmoteList =
                dialogmoteService.getDialogmoteUnfinishedListForVeilederIdent(getNAVIdentFromToken(token))

            val personListWithVeilederAccess = dialogmoteTilgangService.hasAccessToDialogmotePersonList(
                personIdentNumberList = dialogmoteList.map { it.arbeidstaker.personIdent },
                token = token,
                callId = callId,
            ).toHashSet()

            val dialogmoteDTOList =
                dialogmoteList.filter { dialogmote -> personListWithVeilederAccess.contains(dialogmote.arbeidstaker.personIdent) }
                    .map { dialogmote -> dialogmote.toDialogmoteDTO() }

            call.respond(dialogmoteDTOList)
        }

        post(dialogmoteApiPersonIdentUrlPath) {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val newDialogmoteDTO = call.receive<NewDialogmoteDTO>()

            val personidentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personidentNumber,
                action = "Create new Dialogmoteinnkalling"
            ) {
                dialogmoteService.createMoteinnkalling(
                    newDialogmoteDTO = newDialogmoteDTO,
                    token = token,
                    callId = callId,
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
