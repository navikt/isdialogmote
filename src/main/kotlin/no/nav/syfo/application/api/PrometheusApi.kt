package no.nav.syfo.application.api

import io.ktor.server.application.call
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.metric.METRICS_REGISTRY

fun Routing.registerPrometheusApi() {
    get("/metrics") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
