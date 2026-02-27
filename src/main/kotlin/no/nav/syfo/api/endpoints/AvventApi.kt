package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.api.dto.CreateAvventDTO
import no.nav.syfo.api.dto.toAvvent
import no.nav.syfo.application.AvventService


fun Route.registerAvventApi(
    avventService: AvventService,
) {
    route("/api/avvent") {
        post() {
            val avvent = call.receive<CreateAvventDTO>()
                .toAvvent()

            // TODO: validateVeilederAccess
            avventService.persist(avvent)

            call.respond(HttpStatusCode.OK)
        }
    }
}
