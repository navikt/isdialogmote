package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket
import java.util.Properties

fun testEnvironment(
    kafkaBootstrapServers: String,
    isdialogmotepdfgenUrl: String? = null,
    modiasyforestUrl: String? = null,
    syfobehandlendeenhetUrl: String? = null,
    syfomoteadminUrl: String? = null,
    syfopersonUrl: String? = null,
    syfotilgangskontrollUrl: String? = null
) = Environment(
    aadDiscoveryUrl = "",
    loginserviceIdportenDiscoveryUrl = "",
    loginserviceIdportenAudience = listOf("idporten"),
    kafkaBootstrapServers = kafkaBootstrapServers,
    kafkaSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
    redisHost = "localhost",
    redisPort = 6379,
    redisSecret = "password",
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    loginserviceClientId = "123456789",
    dialogmoteArbeidstakerUrl = "https://www.nav.no/dialogmote",
    dialogmoteArbeidsgiverUrl = "https://www.nav.no/dialogmote",
    isdialogmotepdfgenUrl = isdialogmotepdfgenUrl ?: "http://isdialogmotepdfgen",
    modiasyforestUrl = modiasyforestUrl ?: "modiasyforest",
    syfobehandlendeenhetUrl = syfobehandlendeenhetUrl ?: "syfobehandlendeenhet",
    syfomoteadminUrl = syfomoteadminUrl ?: "syfomoteadmin",
    syfopersonUrl = syfopersonUrl ?: "syfoperson",
    syfotilgangskontrollUrl = syfotilgangskontrollUrl ?: "tilgangskontroll",
    mqUsername = "mquser",
    mqPassword = "mqpassword",
    mqTredjepartsVarselQueue = "queuename",
    mqSendingEnabled = false
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}

fun Properties.overrideForTest(): Properties = apply {
    remove("security.protocol")
    remove("sasl.mechanism")
}
