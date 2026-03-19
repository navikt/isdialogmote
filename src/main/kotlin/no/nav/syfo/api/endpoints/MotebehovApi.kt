
package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.BehandleMotebehovDTO
import no.nav.syfo.api.getCallId
import no.nav.syfo.api.getBearerHeader
import no.nav.syfo.api.validateVeilederAccess
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.application.MotebehovService
import no.nav.syfo.domain.PersonIdent

fun Route.registerMotebehovApi(
    motebehovService: MotebehovService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route("/api/motebehov") {
        post("/behandle") {
            val behandleMotebehovDTO = call.receive<BehandleMotebehovDTO>()
            val personident = PersonIdent(behandleMotebehovDTO.personident)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personident,
                action = "Behandle motebehov for Person with PersonIdent",
            ) {
                val token =
                    getBearerHeader()
                        ?: throw IllegalArgumentException("No Authorization header supplied")
                val callId = getCallId()

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = behandleMotebehovDTO.harBehovForMote,
                    tilbakemeldinger = behandleMotebehovDTO.tilbakemeldinger.map { it.toTilbakemelding() },
                    token = token,
                    callId = callId,
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
