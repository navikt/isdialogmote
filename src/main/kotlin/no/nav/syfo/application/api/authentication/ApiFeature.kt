package no.nav.syfo.application.api.authentication

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.util.*
import java.net.URL
import java.util.*
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
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
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
