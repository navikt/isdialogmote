package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState

fun Routing.registerPodApi(
    applicationState: ApplicationState,
    alivenessCheck: () -> Boolean = { applicationState.alive },
    readynessCheck: () -> Boolean = { applicationState.ready }
) {
    get("/is_alive") {
        if (alivenessCheck()) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("/is_ready") {
        if (readynessCheck()) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}
