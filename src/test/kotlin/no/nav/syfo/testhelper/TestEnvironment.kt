package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    dokarkivUrl: String = "http://dokarkiv",
    azureTokenEndpoint: String = "azureTokenEndpoint",
    isdialogmotepdfgenUrl: String? = null,
    modiasyforestUrl: String? = null,
    syfobehandlendeenhetUrl: String? = null,
    syfopersonUrl: String? = null,
    syfotilgangskontrollUrl: String? = null
) = Environment(
    aadDiscoveryUrl = "",
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    azureAppWellKnownUrl = "wellknown",
    dokarkivClientId = "dokarkiv-client-id",
    electorPath = "electorPath",
    loginserviceIdportenDiscoveryUrl = "",
    loginserviceIdportenAudience = listOf("idporten"),
    kafkaBootstrapServers = kafkaBootstrapServers,
    kafkaSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
    kafkaAivenBootstrapServers = kafkaBootstrapServers,
    kafkaAivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
    kafkaAivenRegistryUser = "registryuser",
    kafkaAivenRegistryPassword = "registrypassword",
    kafkaAivenSecurityProtocol = "SSL",
    KafkaAivenCredstorePassword = "credstorepassord",
    KafkaAivenTruststoreLocation = "truststore",
    KafkaAivenKeystoreLocation = "keystore",
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
    dokarkivUrl = dokarkivUrl,
    isdialogmotepdfgenUrl = isdialogmotepdfgenUrl ?: "http://isdialogmotepdfgen",
    modiasyforestUrl = modiasyforestUrl ?: "modiasyforest",
    syfobehandlendeenhetUrl = syfobehandlendeenhetUrl ?: "syfobehandlendeenhet",
    syfopersonClientId = "syfopersonClientId",
    syfopersonUrl = syfopersonUrl ?: "syfoperson",
    syfotilgangskontrollClientId = "syfotilgangskontrollclientid",
    syfotilgangskontrollUrl = syfotilgangskontrollUrl ?: "tilgangskontroll",
    journalforingCronjobEnabled = false,
    publishDialogmoteStatusEndringCronjobEnabled = false,
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
