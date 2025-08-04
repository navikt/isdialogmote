package no.nav.syfo.api.endpoints

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.metric.METRICS_REGISTRY

fun Routing.registerPrometheusApi() {
    get("/metrics") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
