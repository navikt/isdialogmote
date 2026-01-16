package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.authentication.getNAVIdentFromToken
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.application.DialogmoteService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.api.dto.NewDialogmoteDTO
import no.nav.syfo.api.getBearerHeader
import no.nav.syfo.api.getCallId
import no.nav.syfo.api.getPersonIdentHeader
import no.nav.syfo.api.validateVeilederAccess
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdent

const val dialogmoteApiV2Basepath = "/api/v2/dialogmote"

const val dialogmoteApiPersonIdentUrlPath = "/personident"

const val dialogmoteApiVeilederIdentUrlPath = "/veilederident"

fun Route.registerDialogmoteApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
    dialogmotestatusService: DialogmotestatusService,
) {
    route(dialogmoteApiV2Basepath) {
        get(dialogmoteApiPersonIdentUrlPath) {
            val personIdent = getPersonIdentHeader()?.let { personIdent ->
                PersonIdent(personIdent)
            } ?: throw IllegalArgumentException("No PersonIdent supplied")

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personIdent,
                action = "Read Dialogmoter for Person with PersonIdent"
            ) {
                val dialogmoteDTOList = dialogmoteService.getDialogmoteList(
                    personident = personIdent,
                ).map { dialogmote ->
                    DialogmoteDTO.from(dialogmote)
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
                personIdentList = dialogmoteList.map { it.arbeidstaker.personIdent },
                token = token,
                callId = callId,
            ).toHashSet()

            val dialogmoteDTOList =
                dialogmoteList.filter { dialogmote -> personListWithVeilederAccess.contains(dialogmote.arbeidstaker.personIdent) }
                    .map { dialogmote -> DialogmoteDTO.from(dialogmote) }

            call.respond(dialogmoteDTOList)
        }

        post(dialogmoteApiPersonIdentUrlPath) {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val newDialogmoteDTO = call.receive<NewDialogmoteDTO>()

            val personIdent = PersonIdent(newDialogmoteDTO.arbeidstaker.personIdent)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personIdent,
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

        get("/personident/motestatusendringer") {
            val personident = getPersonIdentHeader()?.let { personident ->
                PersonIdent(personident)
            } ?: throw IllegalArgumentException("No Personident supplied")

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personident,
                action = "GET dialogmote statusendringer for Personident"
            ) {
                val dialogmoteStatusEndringer = dialogmotestatusService.getMoteStatusEndringer(personident)

                if (dialogmoteStatusEndringer.isEmpty()) {
                    call.respond(HttpStatusCode.NoContent, dialogmoteStatusEndringer)
                } else {
                    call.respond(dialogmoteStatusEndringer)
                }
            }
        }
    }
}
