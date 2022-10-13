package no.nav.syfo.dialogmote.api.v2

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.domain.toDialogmoteDTO
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.metric.HISTOGRAM_CALL_DIALOGMOTER_ENHET_TIMER
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

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
                val inkluderHistoriske = call.request.queryParameters["inkluderHistoriske"] == "true"
                val enhetNr = call.parameters[dialogmoteApienhetNrParam]?.let { navEnhet -> EnhetNr(navEnhet) }
                    ?: throw IllegalArgumentException("No EnhetNr request param supplied")

                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val accessToEnhet = veilederTilgangskontrollClient.hasAccessToEnhet(
                    enhetNr = enhetNr,
                    token = token,
                    callId = callId
                )
                when (accessToEnhet) {
                    true -> {
                        val starttime = System.currentTimeMillis()
                        val dialogmoteList = if (inkluderHistoriske) dialogmoteService.getDialogmoteList(
                            enhetNr = enhetNr,
                        ) else dialogmoteService.getDialogmoteUnfinishedList(
                            enhetNr = enhetNr,
                        )
                        val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
                        HISTOGRAM_CALL_DIALOGMOTER_ENHET_TIMER.record(duration)

                        val personListWithVeilederAccess = dialogmoteTilgangService.hasAccessToDialogmotePersonList(
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
                        val accessDeniedMessage = "Denied Veileder access to Dialogmoter for EnhetNr $enhetNr"
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
