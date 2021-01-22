package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState

fun Application.apiModule(
    applicationState: ApplicationState
) {
    routing {
        registerPodApi(applicationState)
    }
}
