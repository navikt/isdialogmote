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
import no.nav.syfo.domain.PersonIdent
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
            val personIdent = getPersonIdentHeader()?.let { personIdent ->
                PersonIdent(personIdent)
            } ?: throw IllegalArgumentException("No PersonIdent supplied")

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personIdent,
                action = "Read Dialogmoter for Person with PersonIdent"
            ) {
                val dialogmoteDTOList = dialogmoteService.getDialogmoteList(
                    personIdent = personIdent,
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
                personIdentList = dialogmoteList.map { it.arbeidstaker.personIdent },
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

            val personident = PersonIdent(newDialogmoteDTO.arbeidstaker.personIdent)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personident,
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
