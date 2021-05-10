package no.nav.syfo.application.api.authentication

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.api.authentication")

fun Application.installJwtAuthentication(
    jwtIssuerList: List<JwtIssuer>,
) {
    install(Authentication) {
        jwtIssuerList.forEach { jwtIssuer ->
            configureJwt(
                jwtIssuer = jwtIssuer,
            )
        }
    }
}

fun Authentication.Configuration.configureJwt(
    jwtIssuer: JwtIssuer,
) {
    val jwkProviderSelvbetjening = JwkProviderBuilder(URL(jwtIssuer.wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    jwt(name = jwtIssuer.jwtIssuerType.name) {
        verifier(jwkProviderSelvbetjening, jwtIssuer.wellKnown.issuer)
        validate { credential ->
            if (hasExpectedAudience(credential, jwtIssuer.accectedAudienceList)) {
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

fun hasExpectedAudience(credentials: JWTCredential, expectedAudience: List<String>): Boolean {
    return expectedAudience.any { credentials.payload.audience.contains(it) }
}

fun ApplicationCall.personIdent(): PersonIdentNumber? {
    val principal: JWTPrincipal? = this.authentication.principal()
    return principal?.payload?.subject?.let { PersonIdentNumber(it) }
}

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson(block = configureJacksonMapper())
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception {} {} {}", cause, getCallId(), getConsumerId())
            throw cause
        }
    }
}
