package no.nav.syfo.dialogmote.api.v2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.domain.toDialogmoteDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiEnhetUrlPath = "/enhet"
const val dialogmoteApienhetNrParam = "enhetNr"

fun Route.registerDialogmoteEnhetApiV2(
    dialogmoteService: DialogmoteService,
    dialogmoteTilgangService: DialogmoteTilgangService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(dialogmoteApiV2Basepath) {
        get("$dialogmoteApiEnhetUrlPath/{$dialogmoteApienhetNrParam}") {
            val callId = getCallId()
            try {
                val enhetNr = call.parameters[dialogmoteApienhetNrParam]?.let { navEnhet -> EnhetNr(navEnhet) }
                    ?: throw IllegalArgumentException("No EnhetNr request param supplied")

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val accessToEnhet = veilederTilgangskontrollClient.hasAccessToEnhetWithOBO(
                    enhetNr = enhetNr,
                    token = token,
                    callId = callId
                )
                when (accessToEnhet) {
                    true -> {
                        val dialogmoteList = dialogmoteService.getDialogmoteList(
                            enhetNr = enhetNr,
                        )
                        val personListWithVeilederAccess = dialogmoteTilgangService.hasAccessToDialogmotePersonListWithObo(
                            personIdentNumberList = dialogmoteList.map { it.arbeidstaker.personIdent },
                            token = token,
                            callId = callId,
                        )
                        val dialogmoteDTOList = dialogmoteList.filter { dialogmote ->
                            personListWithVeilederAccess.contains(dialogmote.arbeidstaker.personIdent)
                        }.map { dialogmote ->
                            dialogmote.toDialogmoteDTO()
                        }
                        call.respond(dialogmoteDTOList)
                    }
                    else -> {
                        val accessDeniedMessage = "Denied Veileder access to Dialogmoter for EnhetNr"
                        log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                        call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                    }
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve DialogmoteList for EnhetNr"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callId)
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
