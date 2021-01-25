package no.nav.syfo.application.api

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondTextWriter
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports

fun Routing.registerPrometheusApi(
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
) {
    DefaultExports.initialize()

    get("/prometheus") {
        val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
        call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
            TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
        }
    }
}
