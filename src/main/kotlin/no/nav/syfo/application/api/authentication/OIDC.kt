package no.nav.syfo.application.api.authentication

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.proxyConfig

fun getWellKnown(wellKnownUrl: String) = runBlocking {
    HttpClient(Apache, proxyConfig).use { client ->
        client.get(wellKnownUrl).body<WellKnown>()
    }
}

data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
