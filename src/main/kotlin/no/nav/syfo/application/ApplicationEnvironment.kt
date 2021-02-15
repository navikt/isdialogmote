package no.nav.syfo.application

import io.ktor.application.*

data class Environment(
    val aadDiscoveryUrl: String = getEnvVar("AADDISCOVERY_URL"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaSchemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
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
    val modiasyforestUrl: String = getEnvVar("MODIASYFOREST_URL"),
    val syfomoteadminUrl: String = getEnvVar("SYFOMOTEADMIN_URL"),
    val syfopersonUrl: String = getEnvVar("SYFOPERSON_URL"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL")
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
