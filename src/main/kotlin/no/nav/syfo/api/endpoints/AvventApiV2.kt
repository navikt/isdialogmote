package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.CreateAvventDTO
import no.nav.syfo.api.dto.LukkAvventDTO
import no.nav.syfo.api.dto.QueryAvventDTO
import no.nav.syfo.api.dto.toAvventDTO
import no.nav.syfo.application.AvventService
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.filterPersonsUserHasAccessTo
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident as CommonPersonIdent
import no.nav.syfo.domain.Personident

fun Route.registerAvventApiV2(
    avventService: AvventService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route("/api/v2/avvent") {
        post {
            val avvent = call.receive<CreateAvventDTO>()

            checkPersonAndSyfoTilgang(
                action = "Create Avvent for Person with Personident",
                personident = CommonPersonIdent(avvent.personident),
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val navident = authorizedUser.navident.value
                val avventDTO =
                    avventService
                        .persist(avvent.toAvvent(navident))
                        .toAvventDTO()
                call.respond(HttpStatusCode.OK, avventDTO)
            }
        }

        post("/query") {
            val query = call.receive<QueryAvventDTO>()
            val personIdents = query.personidenter.map { CommonPersonIdent(it) }
            val accessiblePersonIdents = filterPersonsUserHasAccessTo(
                action = "Query Avvent",
                personidenter = personIdents,
                tilgangskontrollClient = tilgangskontrollClient,
            )?.map { Personident(it.value) } ?: emptyList()

            val avventList =
                avventService
                    .getAvventForIdenter(accessiblePersonIdents)
                    .map { it.toAvventDTO() }
            call.respond(avventList)
        }

        post("/lukk") {
            val personident = Personident(call.receive<LukkAvventDTO>().personident)

            checkPersonAndSyfoTilgang(
                action = "Close Avvent for Person with Personident",
                personident = CommonPersonIdent(personident.value),
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { _, _, _ ->
                avventService.lukkAvvent(personident)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
