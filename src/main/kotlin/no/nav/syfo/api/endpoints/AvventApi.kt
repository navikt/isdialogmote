package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.AvventQueryDTO
import no.nav.syfo.api.dto.CreateAvventDTO
import no.nav.syfo.api.dto.toAvvent
import no.nav.syfo.api.validateVeilederAccess
import no.nav.syfo.application.AvventService
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdent
import java.util.UUID

fun Route.registerAvventApi(
    avventService: AvventService,
    dialogmoteTilgangService: DialogmoteTilgangService
) {
    route("/api/avvent") {
        post() {
            val avvent = call.receive<CreateAvventDTO>()
                .toAvvent()

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = avvent.personident,
                action = "Create Avvent for Person with PersonIdent",
            ) {
                avventService.persist(avvent)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/query") {
            val query = call.receive<AvventQueryDTO>()
            val personidenter = query.personidenter.map { personIdent ->
                PersonIdent(personIdent)
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

        post("/{uuid}/lukk") {
            val uuid = UUID.fromString(call.parameters["uuid"])
                ?: throw IllegalArgumentException("No valid UUID supplied")

            val avvent = avventService.getAvvent(uuid)
                ?: return@post call.respond(HttpStatusCode.NotFound)

            validateVeilederAccess(
                dialogmoteTilgangService = dialogmoteTilgangService,
                personIdentToAccess = avvent.personident,
                action = "Lukk Avvent with uuid $uuid",
            ) {
                avventService.lukk(uuid)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
