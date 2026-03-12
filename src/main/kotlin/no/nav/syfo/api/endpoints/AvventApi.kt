package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.authentication.getNAVIdentFromToken
import no.nav.syfo.api.dto.QueryAvventDTO
import no.nav.syfo.api.dto.CreateAvventDTO
import no.nav.syfo.api.dto.toAvvent
import no.nav.syfo.api.getBearerHeader
import no.nav.syfo.api.validateVeilederAccess
import no.nav.syfo.application.AvventService
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdent

fun Route.registerAvventApi(
    avventService: AvventService,
    dialogmoteTilgangService: DialogmoteTilgangService
) {
    route("/api/avvent") {
        post {
            val avvent = call.receive<CreateAvventDTO>()

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = PersonIdent(avvent.personident),
                action = "Create Avvent for Person with PersonIdent",
            ) {
                val token = getBearerHeader()
                    ?: throw IllegalArgumentException("No Authorization header supplied")

                val navident = getNAVIdentFromToken(token)
                val avvent = avventService.persist(avvent.toAvvent(navident))
                call.respond(HttpStatusCode.OK, avvent)
            }
        }

        post("/query") {
            val query = call.receive<QueryAvventDTO>()
            val personidenter = query.personidenter.map { personident ->
                PersonIdent(personident)
            }

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdenterToAccess = personidenter,
                action = "Query Avvent for Person with PersonIdenter",
            ) {
                val avventList = avventService.getAvventForIdenter(personidenter)
                call.respond(avventList)
            }
        }
    }
}
