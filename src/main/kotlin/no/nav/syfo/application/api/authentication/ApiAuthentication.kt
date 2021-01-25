package no.nav.syfo.application.api.authentication

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import net.logstash.logback.argument.StructuredArguments
import java.net.URL
import java.util.concurrent.TimeUnit

fun Application.installJwtAuthentication(
    wellKnown: WellKnown,
    accectedAudienceList: List<String>
) {
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt {
            verifier(jwkProvider, wellKnown.issuer)
            validate { credential ->
                if (hasExpectedAudience(credential, accectedAudienceList)) {
                    JWTPrincipal(credential.payload)
                } else {
                    log.warn(
                        "Auth: Unexpected audience for jwt {}, {}",
                        StructuredArguments.keyValue("issuer", credential.payload.issuer),
                        StructuredArguments.keyValue("audience", credential.payload.audience)
                    )
                    null
                }
            }
        }
    }
}

fun hasExpectedAudience(credentials: JWTCredential, expectedAudience: List<String>): Boolean {
    return expectedAudience.any { credentials.payload.audience.contains(it) }
}
