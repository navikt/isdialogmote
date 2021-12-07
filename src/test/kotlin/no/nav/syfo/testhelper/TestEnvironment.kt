package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    dokarkivUrl: String = "http://dokarkiv",
    azureTokenEndpoint: String = "azureTokenEndpoint",
    isdialogmotepdfgenUrl: String? = null,
    isproxyUrl: String = "isproxy",
    krrUrl: String = "krr",
    syfobehandlendeenhetUrl: String? = null,
    syfotilgangskontrollUrl: String? = null,
    narmestelederUrl: String? = null,
    pdlUrl: String? = null,
    allowVarselMedFysiskBrev: Boolean = false,
    allowMotedeltakerBehandler: Boolean = false,
) = Environment(
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    azureAppWellKnownUrl = "wellknown",
    dokarkivClientId = "dokarkiv-client-id",
    isproxyClientId = "isproxy-client-id",
    isproxyUrl = isproxyUrl,
    electorPath = "electorPath",
    loginserviceIdportenDiscoveryUrl = "",
    loginserviceIdportenAudience = listOf("idporten"),
    kafka = ApplicationEnvironmentKafka(
        bootstrapServers = kafkaBootstrapServers,
        schemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    allowVarselMedFysiskBrev = allowVarselMedFysiskBrev,
    allowMotedeltakerBehandler = allowMotedeltakerBehandler,
    redisHost = "localhost",
    redisPort = 6599,
    redisSecret = "password",
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    dialogmoteArbeidstakerUrl = "https://www.nav.no/dialogmote",
    dokarkivUrl = dokarkivUrl,
    isdialogmotepdfgenUrl = isdialogmotepdfgenUrl ?: "http://isdialogmotepdfgen",
    krrClientId = "dev-gcp.team-rocket.digdir-krr-proxy",
    krrUrl = krrUrl,
    syfobehandlendeenhetClientId = "syfobehandlendeenhetClientId",
    syfobehandlendeenhetUrl = syfobehandlendeenhetUrl ?: "syfobehandlendeenhet",
    syfotilgangskontrollClientId = "syfotilgangskontrollclientid",
    syfotilgangskontrollUrl = syfotilgangskontrollUrl ?: "tilgangskontroll",
    journalforingCronjobEnabled = false,
    publishDialogmoteStatusEndringCronjobEnabled = false,
    mqUsername = "mquser",
    mqPassword = "mqpassword",
    mqTredjepartsVarselQueue = "queuename",
    mqSendingEnabled = false,
    narmestelederClientId = "narmestelederClientId",
    narmestelederUrl = narmestelederUrl ?: "http://narmesteleder",
    pdlClientId = "pdlClientId",
    pdlUrl = pdlUrl ?: "http://pdl",
    toggleKafkaProcessingDialogmeldinger = true,
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
