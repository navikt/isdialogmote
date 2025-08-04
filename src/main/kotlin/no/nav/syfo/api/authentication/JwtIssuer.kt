package no.nav.syfo.api.authentication

data class JwtIssuer(
    val acceptedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    SELVBETJENING,
    VEILEDER_V2,
}
