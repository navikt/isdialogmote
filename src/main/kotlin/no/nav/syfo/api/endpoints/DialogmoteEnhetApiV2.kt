package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.application.DialogmoteService
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.tilgangskontroll.filterPersonsUserHasAccessTo
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.metric.HISTOGRAM_CALL_DIALOGMOTER_ENHET_TIMER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val dialogmoteApiEnhetUrlPath = "/enhet"
const val dialogmoteApienhetNrParam = "enhetNr"

fun Route.registerDialogmoteEnhetApiV2(
    dialogmoteService: DialogmoteService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route(dialogmoteApiV2Basepath) {
        get("$dialogmoteApiEnhetUrlPath/{$dialogmoteApienhetNrParam}") {
            try {
                val inkluderHistoriske = call.request.queryParameters["inkluderHistoriske"] == "true"
                val enhetNr =
                    call.parameters[dialogmoteApienhetNrParam]
                        ?.let { navEnhet -> EnhetNr(navEnhet) }
                        ?: throw IllegalArgumentException("No EnhetNr request param supplied")

                val starttime = System.currentTimeMillis()
                val dialogmoteList =
                    if (inkluderHistoriske) {
                        dialogmoteService.getDialogmoteList(
                            enhetNr = enhetNr,
                        )
                    } else {
                        dialogmoteService.getDialogmoteUnfinishedList(
                            enhetNr = enhetNr,
                        )
                    }
                val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
                HISTOGRAM_CALL_DIALOGMOTER_ENHET_TIMER.record(duration)

                val personidenter = dialogmoteList.map { Personident(it.arbeidstaker.personident.value) }
                val accessiblePersonIdentValues =
                    filterPersonsUserHasAccessTo(
                        action = "Get Dialogmote list for EnhetNr",
                        personidenter = personidenter,
                        tilgangskontrollClient = tilgangskontrollClient,
                    )?.map { it.value }?.toHashSet() ?: emptySet()

                val dialogmoteDTOList =
                    dialogmoteList
                        .filter { dialogmote ->
                            dialogmote.arbeidstaker.personident.value in accessiblePersonIdentValues
                        }.map { dialogmote ->
                            DialogmoteDTO.from(dialogmote)
                        }
                call.respond(dialogmoteDTOList)
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve DialogmoteList for EnhetNr"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, call.callId)
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
