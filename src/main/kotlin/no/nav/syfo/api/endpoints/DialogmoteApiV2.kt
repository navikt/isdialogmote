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
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.filterPersonsUserHasAccessTo
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident as CommonPersonIdent
import no.nav.syfo.domain.Personident

const val dialogmoteApiV2Basepath = "/api/v2/dialogmote"

const val dialogmoteApiPersonIdentUrlPath = "/personident"

const val dialogmoteApiVeilederIdentUrlPath = "/veilederident"

fun Route.registerDialogmoteApiV2(
    dialogmoteService: DialogmoteService,
    tilgangskontrollClient: TilgangskontrollClient,
    dialogmotestatusService: DialogmotestatusService,
) {
    route(dialogmoteApiV2Basepath) {
        get(dialogmoteApiPersonIdentUrlPath) {
            checkPersonAndSyfoTilgang(
                action = "Read Dialogmoter for Person with Personident",
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = false,
            ) { _, targetPersonIdent, _ ->
                val personident = Personident(targetPersonIdent.value)
                val dialogmoteDTOList = dialogmoteService.getDialogmoteList(
                    personident = personident,
                ).map { dialogmote ->
                    DialogmoteDTO.from(dialogmote)
                }
                call.respond(dialogmoteDTOList)
            }
        }
        get(dialogmoteApiVeilederIdentUrlPath) {
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")

            val dialogmoteList =
                dialogmoteService.getDialogmoteUnfinishedListForVeilederIdent(getNAVIdentFromToken(token))

            val personIdents = dialogmoteList.map { CommonPersonIdent(it.arbeidstaker.personident.value) }
            val accessiblePersonIdentValues = filterPersonsUserHasAccessTo(
                action = "Get Dialogmoter for VeilederIdent",
                personidenter = personIdents,
                tilgangskontrollClient = tilgangskontrollClient,
            )?.map { it.value }?.toHashSet() ?: emptySet()

            val dialogmoteDTOList = dialogmoteList
                .filter { dialogmote -> dialogmote.arbeidstaker.personident.value in accessiblePersonIdentValues }
                .map { dialogmote -> DialogmoteDTO.from(dialogmote) }

            call.respond(dialogmoteDTOList)
        }

        post(dialogmoteApiPersonIdentUrlPath) {
            val newDialogmoteDTO = call.receive<NewDialogmoteDTO>()

            checkPersonAndSyfoTilgang(
                action = "Create new Dialogmoteinnkalling",
                personident = CommonPersonIdent(newDialogmoteDTO.arbeidstaker.personident),
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, callId ->
                dialogmoteService.createMoteinnkalling(
                    newDialogmoteDTO = newDialogmoteDTO,
                    token = authorizedUser.token,
                    callId = callId,
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/personident/motestatusendringer") {
            checkPersonAndSyfoTilgang(
                action = "GET dialogmote statusendringer for Personident",
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = false,
            ) { _, targetPersonIdent, _ ->
                val personident = Personident(targetPersonIdent.value)
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
