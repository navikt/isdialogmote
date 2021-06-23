package no.nav.syfo.application.api.authentication

data class JwtIssuer(
    val accectedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    SELVBETJENING,
    VEILEDER,
    VEILEDER_V2,
}
