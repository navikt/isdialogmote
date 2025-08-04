package no.nav.syfo.testhelper

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import no.nav.syfo.api.authentication.JWT_CLAIM_AZP
import no.nav.syfo.api.authentication.JWT_CLAIM_NAVIDENT
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

const val keyId = "localhost-signer"

fun generateJWTNavIdent(
    audience: String,
    issuer: String,
    navIdent: String,
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1)
): String = generateJwt(
    audience = audience,
    claimValueMap = mapOf(
        JWT_CLAIM_AZP to UserConstants.JWT_AZP,
        JWT_CLAIM_NAVIDENT to navIdent,
    ),
    issuer = issuer,
    expiry = expiry,
)

fun generateJWTIdporten(
    audience: String,
    issuer: String,
    pid: String? = "pid",
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1)
): String = generateJwt(
    audience = audience,
    claimValueMap = mapOf(
        "pid" to pid,
    ),
    issuer = issuer,
    expiry = expiry,
)

fun generateJWTTokenx(
    audience: String,
    issuer: String,
    pid: String? = "pid",
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1)
): String = generateJwt(
    audience = audience,
    claimValueMap = mapOf(
        "pid" to pid,
    ),
    issuer = issuer,
    expiry = expiry,
)

/* Utsteder en Bearer-token (En slik vi ber AzureAd om). OBS: Det er viktig at KeyId matcher kid i jwkset.json  */
private fun generateJwt(
    audience: String,
    claimValueMap: Map<String, String?>,
    issuer: String,
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1),
): String {
    val now = Date()

    val baseJwtBuilder = JWT.create()
        .withKeyId(keyId)
        .withIssuer(issuer)
        .withAudience(audience)
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("ver", "1.0")
        .withClaim("nonce", "myNonce")
        .withClaim("auth_time", now)
        .withClaim("nbf", now)
        .withClaim("iat", now)
        .withClaim("exp", Date.from(expiry?.atZone(ZoneId.systemDefault())?.toInstant()))

    claimValueMap.entries.forEach { claimKeyValue ->
        baseJwtBuilder.withClaim(claimKeyValue.key, claimKeyValue.value)
    }

    val key = getDefaultRSAKey()
    val alg = Algorithm.RSA256(key.toRSAPublicKey(), key.toRSAPrivateKey())

    return baseJwtBuilder.sign(alg)
}

fun getDefaultRSAKey(): RSAKey {
    return getJWKSet().getKeyByKeyId(keyId) as RSAKey
}

private fun getJWKSet(): JWKSet {
    try {
        return JWKSet.parse(getFileAsString("src/test/resources/jwkset.json"))
    } catch (io: IOException) {
        throw RuntimeException(io)
    } catch (io: ParseException) {
        throw RuntimeException(io)
    }
}

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
