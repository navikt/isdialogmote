
package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.MotebehovVurderingDTO
import no.nav.syfo.application.MotebehovService
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.common.types.ident.Personident as CommonPersonIdent

fun Route.registerMotebehovApiV2(
    motebehovService: MotebehovService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route("/api/v2/motebehov") {
        post("/vurderinger") {
            val vurdering = call.receive<MotebehovVurderingDTO>()
            val personident = Personident(vurdering.personident)

            checkPersonAndSyfoTilgang(
                action = "Behandle motebehov for Person with Personident",
                personident = CommonPersonIdent(personident.value),
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = vurdering.harBehovForMote,
                    tilbakemeldinger = vurdering.tilbakemeldinger.map { it.toTilbakemelding() },
                    token = authorizedUser.token,
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
