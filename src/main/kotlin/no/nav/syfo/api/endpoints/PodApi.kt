package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface

fun Routing.registerPodApi(
    applicationState: ApplicationState,
    database: DatabaseInterface,
) {
    get("/internal/is_alive") {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("/internal/is_ready") {
        if (isReady(applicationState, database)) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun isReady(applicationState: ApplicationState, database: DatabaseInterface): Boolean {
    return applicationState.ready && database.isOk()
}

private fun DatabaseInterface.isOk(): Boolean {
    return try {
        connection.use {
            it.isValid(1)
        }
    } catch (ex: Exception) {
        false
    }
}
