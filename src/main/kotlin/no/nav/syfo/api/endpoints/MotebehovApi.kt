
package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.MotebehovVurderingDTO
import no.nav.syfo.api.validateVeilederAccess
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.application.MotebehovService
import no.nav.syfo.domain.PersonIdent

fun Route.registerMotebehovApi(
    motebehovService: MotebehovService,
    dialogmoteTilgangService: DialogmoteTilgangService,
) {
    route("/api/motebehov") {
        post("/vurderinger") {
            val vurdering = call.receive<MotebehovVurderingDTO>()
            val personident = PersonIdent(vurdering.personident)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = personident,
                action = "Behandle motebehov for Person with PersonIdent",
            ) { token ->
                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = vurdering.harBehovForMote,
                    tilbakemeldinger = vurdering.tilbakemeldinger.map { it.toTilbakemelding() },
                    token = token,
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
