package no.nav.syfo.application

data class Environment(
    val aadDiscoveryUrl: String = getEnvVar("AADDISCOVERY_URL"),
    val loginserviceClientId: String = getEnvVar("LOGINSERVICE_CLIENT_ID")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
