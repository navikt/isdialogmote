package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket

fun testEnvironment(
    kafkaBootstrapServers: String,
    modiasyforestUrl: String? = null,
    syfomoteadminUrl: String? = null,
    syfopersonUrl: String? = null,
    syfotilgangskontrollUrl: String? = null
) = Environment(
    aadDiscoveryUrl = "",
    kafkaBootstrapServers = kafkaBootstrapServers,
    kafkaSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    loginserviceClientId = "123456789",
    dialogmoteArbeidstakerUrl = "https://www.nav.no/dialogmote",
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
