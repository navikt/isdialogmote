package no.nav.syfo.api.authentication

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.httpClientProxy

private val httpClient = httpClientProxy()

fun getWellKnown(wellKnownUrl: String) = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnown>()
}

data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)
