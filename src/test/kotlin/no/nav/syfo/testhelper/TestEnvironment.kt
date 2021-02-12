package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket

fun testEnvironment(
    modiasyforestUrl: String? = null,
    syfomoteadminUrl: String? = null,
    syfopersonUrl: String? = null,
    syfotilgangskontrollUrl: String? = null
) = Environment(
    aadDiscoveryUrl = "",
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    loginserviceClientId = "123456789",
    modiasyforestUrl = modiasyforestUrl ?: "modiasyforest",
    syfomoteadminUrl = syfomoteadminUrl ?: "syfomoteadmin",
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