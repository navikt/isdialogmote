package no.nav.syfo.testhelper

import no.nav.syfo.ApplicationEnvironmentKafka
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.client.cache.ValkeyConfig
import java.net.URI
import java.time.LocalDate

fun testEnvironment() = Environment(
    cluster = "local",
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = "azureTokenEndpoint",
    azureAppWellKnownUrl = "wellknown",
    tokenxClientId = "tokenx-client-id",
    tokenxEndpoint = "tokenxEndpoint",
    tokenxPrivateJWK = getDefaultRSAKey().toJSONString(),
    tokenxWellKnownUrl = "tokenx-wellknown",
    dokarkivClientId = "dokarkiv-client-id",
    isoppfolgingstilfelleClientId = "isoppfolgingstilfelle-client-id",
    isoppfolgingstilfelleUrl = "isoppfolgingstilfelleUrl",
    eregUrl = "eregUrl",
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
    valkeyConfig = ValkeyConfig(
        valkeyUri = URI("http://localhost:6379"),
        valkeyDB = 0,
        valkeyUsername = "valkeyUser",
        valkeyPassword = "valkeyPassword",
        ssl = false,
    ),
    isdialogmoteDbHost = "localhost",
    isdialogmoteDbPort = "5432",
    isdialogmoteDbName = "isdialogmote_dev",
    isdialogmoteDbUsername = "username",
    isdialogmoteDbPassword = "password",
    dokarkivUrl = "dokarkivUrl",
    ispdfgenUrl = "ispdfgenUrl",
    krrClientId = "dev-gcp.team-rocket.digdir-krr-proxy",
    krrUrl = "krrUrl",
    syfobehandlendeenhetClientId = "syfobehandlendeenhetClientId",
    syfobehandlendeenhetUrl = "syfobehandlendeenhet",
    istilgangskontrollClientId = "tilgangskontrollclientid",
    istilgangskontrollUrl = "tilgangskontroll",
    narmestelederClientId = "narmestelederClientId",
    narmestelederUrl = "narmestelederUrl",
    pdlClientId = "pdlClientId",
    pdlUrl = "pdlUrl",
    dialogmeldingUrl = "dialogmeldingUrl",
    dialogmeldingClientId = "dialogmeldingClientId",
    altinnWsUrl = "altinnUrl",
    altinnUsername = "username",
    altinnPassword = "password",
    altinnSendingEnabled = true,
    dokumentportenUrl = "dokumentportenUrl",
    dokumentportenClientId = "api://dokumentporten/.default",
    dokumentportenSendingEnabled = true,
    outdatedDialogmoteCutoff = LocalDate.parse("2022-07-01"),
    isJournalforingRetryEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)
