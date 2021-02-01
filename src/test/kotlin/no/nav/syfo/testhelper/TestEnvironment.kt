package no.nav.syfo.testhelper

import no.nav.syfo.application.Environment
import java.net.ServerSocket

fun testEnvironment() = Environment(
    aadDiscoveryUrl = "",
    loginserviceClientId = "123456789",
    syfopersonUrl = "syfoperson",
    syfotilgangskontrollUrl = "tilgangskontroll"
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
