package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import no.nav.syfo.util.configuredJacksonMapper

fun httpClientDefault() = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer(configuredJacksonMapper())
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 120000
        connectTimeoutMillis = 60000
    }
}
