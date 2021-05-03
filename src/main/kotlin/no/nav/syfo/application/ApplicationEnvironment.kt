package no.nav.syfo.application

import io.ktor.application.*

data class Environment(
    val aadDiscoveryUrl: String = getEnvVar("AADDISCOVERY_URL"),
    val aadAppClient: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val aadAppSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val dokarkivClientId: String = getEnvVar("JOARK_CLIENT_ID"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaSchemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
    val redisHost: String = getEnvVar("REDIS_HOST"),
    val redisPort: Int = getEnvVar("REDIS_PORT", "6379").toInt(),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD"),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val isdialogmoteDbHost: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_HOST"),
    val isdialogmoteDbPort: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PORT"),
    val isdialogmoteDbName: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_DATABASE"),
    val isdialogmoteDbUsername: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_USERNAME"),
    val isdialogmoteDbPassword: String = getEnvVar("NAIS_DATABASE_ISDIALOGMOTE_ISDIALOGMOTE_DB_PASSWORD"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "30000").toLong(),
    val loginserviceClientId: String = getEnvVar("LOGINSERVICE_CLIENT_ID"),
    val dialogmoteArbeidstakerUrl: String = getEnvVar("DIALOGMOTE_ARBEIDSTAKER_URL"),
    val dialogmoteArbeidsgiverUrl: String = getEnvVar("DIALOGMOTE_ARBEIDSGIVER_URL"),
    val isdialogmotepdfgenUrl: String = "http://isdialogmotepdfgen",
    val modiasyforestUrl: String = getEnvVar("MODIASYFOREST_URL"),
    val syfobehandlendeenhetUrl: String = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
    val syfomoteadminUrl: String = getEnvVar("SYFOMOTEADMIN_URL"),
    val syfopersonUrl: String = getEnvVar("SYFOPERSON_URL"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val journalforingCronjobEnabled: Boolean = getEnvVar("TOGGLE_JOURNALFORING_CRONJOB_ENABLED").toBoolean(),
    val mqChannelName: String = getEnvVar("MQGATEWAY_CHANNEL_NAME", "DEV.APP.SVRCONN"),
    val mqHostname: String = getEnvVar("MQGATEWAY_HOSTNAME", "localhost"),
    val mqQueueManager: String = getEnvVar("MQGATEWAY_NAME", "QM1"),
    val mqPort: Int = getEnvVar("MQGATEWAY_PORT", "1414").toInt(),
    val mqApplicationName: String = "isdialogmote",
    val mqUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val mqPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val mqTredjepartsVarselQueue: String = getEnvVar("TREDJEPARTSVARSEL_QUEUENAME"),
    val mqSendingEnabled: Boolean = getEnvVar("TOGGLE_MQ_SENDING_ENABLED").toBoolean()
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$isdialogmoteDbHost:$isdialogmoteDbPort/$isdialogmoteDbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
