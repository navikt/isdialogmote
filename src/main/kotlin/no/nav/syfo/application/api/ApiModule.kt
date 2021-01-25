package no.nav.syfo.application.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.api.authentication.installJwtAuthentication
import no.nav.syfo.dialogmote.registerDialogmoteApi
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getConsumerId
import java.util.*

fun Application.apiModule(
    applicationState: ApplicationState,
    environment: Environment
) {
    val wellKnown = getWellKnown(environment.aadDiscoveryUrl)
    installJwtAuthentication(
        wellKnown,
        listOf(environment.loginserviceClientId)
    )

    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause, getCallId(), getConsumerId())
            throw cause
        }
    }

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        authenticate {
            registerDialogmoteApi()
        }
    }
}
