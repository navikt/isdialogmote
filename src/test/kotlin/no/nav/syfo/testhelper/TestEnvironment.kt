package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import java.net.ServerSocket
import java.time.LocalDate

fun testEnvironment(
    kafkaBootstrapServers: String,
    dokarkivUrl: String = "http://dokarkiv",
    azureTokenEndpoint: String = "azureTokenEndpoint",
    tokenxEndpoint: String = "tokenxEndpoint",
    isdialogmotepdfgenUrl: String? = null,
    isoppfolgingstilfelleUrl: String = "isoppfolgingstilfelle",
    eregUrl: String = "ereg",
    krrUrl: String = "krr",
    syfobehandlendeenhetUrl: String? = null,
    syfotilgangskontrollUrl: String? = null,
    narmestelederUrl: String? = null,
    pdlUrl: String? = null,
) = Environment(
    cluster = "local",
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    azureAppWellKnownUrl = "wellknown",
    tokenxClientId = "tokenx-client-id",
    tokenxEndpoint = tokenxEndpoint,
    tokenxPrivateJWK = getDefaultRSAKey().toJSONString(),
    tokenxWellKnownUrl = "tokenx-wellknown",
    dokarkivClientId = "dokarkiv-client-id",
    isoppfolgingstilfelleClientId = "isoppfolgingstilfelle-client-id",
    isoppfolgingstilfelleUrl = isoppfolgingstilfelleUrl,
    eregUrl = eregUrl,
    electorPath = "electorPath",
    kafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    redisHost = "localhost",
    redisPort = 6599,
    redisSecret = "password",
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    dokarkivUrl = dokarkivUrl,
    isdialogmotepdfgenUrl = isdialogmotepdfgenUrl ?: "http://isdialogmotepdfgen",
    krrClientId = "dev-gcp.team-rocket.digdir-krr-proxy",
    krrUrl = krrUrl,
    syfobehandlendeenhetClientId = "syfobehandlendeenhetClientId",
    syfobehandlendeenhetUrl = syfobehandlendeenhetUrl ?: "syfobehandlendeenhet",
    syfotilgangskontrollClientId = "syfotilgangskontrollclientid",
    syfotilgangskontrollUrl = syfotilgangskontrollUrl ?: "tilgangskontroll",
    narmestelederClientId = "narmestelederClientId",
    narmestelederUrl = narmestelederUrl ?: "http://narmesteleder",
    pdlClientId = "pdlClientId",
    pdlUrl = pdlUrl ?: "http://pdl",
    altinnWsUrl = "altinnUrl",
    altinnUsername = "username",
    altinnPassword = "password",
    altinnSendingEnabled = true,
    outdatedDialogmoteCronJobEnabled = true,
    outdatedDialogmoteCutoff = LocalDate.parse("2022-07-01"),
    kode6Enabled = true,
    publishDialogmotesvarEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
