package no.nav.syfo

import io.ktor.server.application.*
import no.nav.syfo.infrastructure.client.cache.ValkeyConfig
import java.net.URI
import java.time.LocalDate

data class Environment(
    val namespace: String = "teamsykefravr",
    val appname: String = "isdialogmote",
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val aadAppClient: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val aadAppSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val azureAppWellKnownUrl: String = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
    val tokenxClientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val tokenxEndpoint: String = getEnvVar("TOKEN_X_TOKEN_ENDPOINT"),
    val tokenxWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val tokenxPrivateJWK: String = getEnvVar("TOKEN_X_PRIVATE_JWK"),
    val dokarkivClientId: String = getEnvVar("DOKARKIV_CLIENT_ID"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val kafka: ApplicationEnvironmentKafka = ApplicationEnvironmentKafka(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
    ),
    val valkeyConfig: ValkeyConfig = ValkeyConfig(
        valkeyUri = URI(getEnvVar("VALKEY_URI_CACHE")),
        valkeyDB = 7, // se https://github.com/navikt/istilgangskontroll/blob/master/README.md
        valkeyUsername = getEnvVar("VALKEY_USERNAME_CACHE"),
        valkeyPassword = getEnvVar("VALKEY_PASSWORD_CACHE"),
    ),
    val isdialogmoteDbHost: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_HOST"),
    val isdialogmoteDbPort: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PORT"),
    val isdialogmoteDbName: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_DATABASE"),
    val isdialogmoteDbUsername: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_USERNAME"),
    val isdialogmoteDbPassword: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PASSWORD"),

    val isoppfolgingstilfelleClientId: String = getEnvVar("ISOPPFOLGINGSTILFELLE_CLIENT_ID"),
    val isoppfolgingstilfelleUrl: String = getEnvVar("ISOPPFOLGINGSTILFELLE_URL"),

    val eregUrl: String = getEnvVar("EREG_URL"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "30000").toLong(),
    val dokarkivUrl: String = getEnvVar("DOKARKIV_URL"),
    val ispdfgenUrl: String = "http://ispdfgen",
    val krrClientId: String = getEnvVar("KRR_CLIENT_ID"),
    val krrUrl: String = getEnvVar("KRR_URL"),
    val syfobehandlendeenhetClientId: String = getEnvVar("SYFOBEHANDLENDEENHET_CLIENT_ID"),
    val syfobehandlendeenhetUrl: String = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
    val istilgangskontrollClientId: String = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID"),
    val istilgangskontrollUrl: String = getEnvVar("ISTILGANGSKONTROLL_URL"),
    val narmestelederUrl: String = getEnvVar("NARMESTELEDER_URL"),
    val narmestelederClientId: String = getEnvVar("NARMESTELEDER_CLIENT_ID"),
    val pdlUrl: String = getEnvVar("PDL_URL"),
    val pdlClientId: String = getEnvVar("PDL_CLIENT_ID"),
    val dialogmeldingUrl: String = getEnvVar("DIALOGMELDING_URL"),
    val dialogmeldingClientId: String = getEnvVar("DIALOGMELDING_CLIENT_ID"),
    val altinnWsUrl: String = getEnvVar("ALTINN_WS_URL"),
    val altinnUsername: String = getEnvVar("ALTINN_USERNAME"),
    val altinnPassword: String = getEnvVar("ALTINN_PASSWORD"),
    val altinnSendingEnabled: Boolean = getEnvVar("ALTINN_SENDING_ENABLED").toBoolean(),
    val dokumentportenUrl: String = getEnvVar("DOKUMENTPORTEN_URL"),
    val dokumentportenClientId: String = getEnvVar("DOKUMENTPORTEN_CLIENT_ID"),
    val dokumentportenSendingEnabled: Boolean = getEnvVar("DOKUMENTPORTEN_SENDING_ENABLED").toBoolean(),
    val outdatedDialogmoteCutoff: LocalDate = LocalDate.parse(getEnvVar("OUTDATED_DIALOGMOTE_CUTOFF")),
    val isJournalforingRetryEnabled: Boolean = getEnvVar("JOURNALFORING_RETRY_ENABLED").toBoolean(),
) {

    fun jdbcUrl(): String {
        return "jdbc:postgresql://$isdialogmoteDbHost:$isdialogmoteDbPort/$isdialogmoteDbName"
    }
}

data class ApplicationEnvironmentKafka(
    val aivenBootstrapServers: String,
    val aivenSchemaRegistryUrl: String,
    val aivenRegistryUser: String,
    val aivenRegistryPassword: String,
    val aivenSecurityProtocol: String,
    val aivenCredstorePassword: String,
    val aivenTruststoreLocation: String,
    val aivenKeystoreLocation: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}

val DEV_GCP = "dev-gcp"

fun Environment.isDevGcp() = DEV_GCP == cluster
