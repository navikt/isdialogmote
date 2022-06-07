package no.nav.syfo.application

import io.ktor.server.application.*

data class Environment(
    val namespace: String = "teamsykefravr",
    val appname: String = "isdialogmote",
    val aadAppClient: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val aadAppSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val azureAppWellKnownUrl: String = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
    val dokarkivClientId: String = getEnvVar("DOKARKIV_CLIENT_ID"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
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
    val redisHost: String = getEnvVar("REDIS_HOST"),
    val redisPort: Int = getEnvVar("REDIS_PORT", "6379").toInt(),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD"),
    val isdialogmoteDbHost: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_HOST"),
    val isdialogmoteDbPort: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PORT"),
    val isdialogmoteDbName: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_DATABASE"),
    val isdialogmoteDbUsername: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_USERNAME"),
    val isdialogmoteDbPassword: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PASSWORD"),

    val isoppfolgingstilfelleClientId: String = getEnvVar("ISOPPFOLGINGSTILFELLE_CLIENT_ID"),
    val isoppfolgingstilfelleUrl: String = getEnvVar("ISOPPFOLGINGSTILFELLE_URL"),

    val isproxyUrl: String = getEnvVar("ISPROXY_URL"),
    val isproxyClientId: String = getEnvVar("ISPROXY_CLIENT_ID"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "30000").toLong(),
    val dialogmoteArbeidstakerUrl: String = getEnvVar("DIALOGMOTE_ARBEIDSTAKER_URL"),
    val dokarkivUrl: String = getEnvVar("DOKARKIV_URL"),
    val isdialogmotepdfgenUrl: String = "http://isdialogmotepdfgen",
    val krrClientId: String = getEnvVar("KRR_CLIENT_ID"),
    val krrUrl: String = getEnvVar("KRR_URL"),
    val syfobehandlendeenhetClientId: String = getEnvVar("SYFOBEHANDLENDEENHET_CLIENT_ID"),
    val syfobehandlendeenhetUrl: String = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val mqChannelName: String = getEnvVar("MQGATEWAY_CHANNEL_NAME", "DEV.APP.SVRCONN"),
    val mqHostname: String = getEnvVar("MQGATEWAY_HOSTNAME", "localhost"),
    val mqQueueManager: String = getEnvVar("MQGATEWAY_NAME", "QM1"),
    val mqPort: Int = getEnvVar("MQGATEWAY_PORT", "1414").toInt(),
    val mqApplicationName: String = "isdialogmote",
    val mqUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val mqPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val mqTredjepartsVarselQueue: String = getEnvVar("TREDJEPARTSVARSEL_QUEUENAME"),
    val narmestelederUrl: String = getEnvVar("NARMESTELEDER_URL"),
    val narmestelederClientId: String = getEnvVar("NARMESTELEDER_CLIENT_ID"),
    val pdlUrl: String = getEnvVar("PDL_URL"),
    val pdlClientId: String = getEnvVar("PDL_CLIENT_ID"),
    val altinnWsUrl: String = getEnvVar("ALTINN_WS_URL"),
    val altinnUsername: String = getEnvVar("ALTINN_USERNAME"),
    val altinnPassword: String = getEnvVar("ALTINN_PASSWORD"),
    val dokdistFordelingUrl: String = getEnvVar("DOKDIST_FORDELING_URL"),
    val dokdistFordelingClientId: String = getEnvVar("DOKDIST_FORDELING_CLIENT_ID"),
    val altinnNotificationEnabled: Boolean = getEnvVar("ALTINN_NOTIFICATION_ENABLED").toBoolean(),
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
