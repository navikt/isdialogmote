package no.nav.syfo.application.api.authentication

import com.auth0.jwt.JWT

const val JWT_CLAIM_NAVIDENT = "NAVident"

fun getNAVIdentFromToken(token: String): String {
    val decodedJWT = JWT.decode(token)
    val navIdent: String = decodedJWT.claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw Error("Missing NAVident in private claims")
    return navIdent
}
