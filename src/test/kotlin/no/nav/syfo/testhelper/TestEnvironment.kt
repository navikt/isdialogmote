package no.nav.syfo.testhelper

import io.ktor.utils.io.core.*
import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.cache.RedisConfig
import java.net.ServerSocket
import java.net.URI
import java.time.LocalDate

fun testEnvironment(
    dokarkivUrl: String = "http://dokarkiv",
    azureTokenEndpoint: String = "azureTokenEndpoint",
    tokenxEndpoint: String = "tokenxEndpoint",
    ispdfgenUrl: String? = null,
    isoppfolgingstilfelleUrl: String = "isoppfolgingstilfelle",
    eregUrl: String = "ereg",
    krrUrl: String = "krr",
    syfobehandlendeenhetUrl: String? = null,
    tilgangskontrollUrl: String? = null,
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
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    redisConfig = RedisConfig(
        redisUri = URI("http://localhost:6379"),
        redisDB = 0,
        redisUsername = "redisUser",
        redisPassword = "redisPassword",
        ssl = false,
    ),
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    dokarkivUrl = dokarkivUrl,
    ispdfgenUrl = ispdfgenUrl ?: "http://ispdfgen",
    krrClientId = "dev-gcp.team-rocket.digdir-krr-proxy",
    krrUrl = krrUrl,
    syfobehandlendeenhetClientId = "syfobehandlendeenhetClientId",
    syfobehandlendeenhetUrl = syfobehandlendeenhetUrl ?: "syfobehandlendeenhet",
    istilgangskontrollClientId = "tilgangskontrollclientid",
    istilgangskontrollUrl = tilgangskontrollUrl ?: "tilgangskontroll",
    narmestelederClientId = "narmestelederClientId",
    narmestelederUrl = narmestelederUrl ?: "http://narmesteleder",
    pdlClientId = "pdlClientId",
    pdlUrl = pdlUrl ?: "http://pdl",
    altinnWsUrl = "altinnUrl",
    altinnUsername = "username",
    altinnPassword = "password",
    altinnSendingEnabled = true,
    outdatedDialogmoteCutoff = LocalDate.parse("2022-07-01"),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
