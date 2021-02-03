package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket

fun testEnvironment(
    syfopersonUrl: String? = null,
    syfotilgangskontrollUrl: String? = null
) = Environment(
    aadDiscoveryUrl = "",
    loginserviceClientId = "123456789",
    syfopersonUrl = syfopersonUrl ?: "syfoperson",
    syfotilgangskontrollUrl = syfotilgangskontrollUrl ?: "tilgangskontroll"
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
