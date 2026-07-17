package no.nav.syfo.api.authentication

import com.auth0.jwt.JWT

const val JWT_CLAIM_NAVIDENT = "NAVident"
const val JWT_CLAIM_AZP = "azp"

fun getNAVIdentFromToken(token: String): String {
    val decodedJWT = JWT.decode(token)
    val navident: String = decodedJWT.claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw Error("Missing NAVident in private claims")
    return navident
}

fun getConsumerClientId(token: String): String {
    val decodedJWT = JWT.decode(token)
    return decodedJWT.claims[JWT_CLAIM_AZP]?.asString()
        ?: throw RuntimeException("Missing azp in private claims")
}
