package no.nav.syfo.client.veiledertilgang

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val tilgangskontrollBaseUrl: String
) {
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
    }

    suspend fun hasAccessToPersonList(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String
    ): List<PersonIdentNumber> {
        try {
            val personIdentStringList = personIdentNumberList.map { it.value }

            val response: HttpResponse = httpClient.post(getTilgangskontrollUrl()) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = personIdentStringList
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS.inc()
            return response.receive<List<String>>().map { personIdent -> PersonIdentNumber(personIdent) }
        } catch (e: ClientRequestException) {
            return if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN.inc()
                emptyList()
            } else {
                handleUnexpectedResponseException(e.response, resourceEnhet, callId)
                return emptyList()
            }
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, resourceEnhet, callId)
            return emptyList()
        }
    }

    private fun getTilgangskontrollUrl(): String {
        return "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/brukere"
    }

    suspend fun hasAccess(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String
    ): Boolean {
        try {
            val response: HttpResponse = httpClient.get(getTilgangskontrollUrl(personIdentNumber)) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.inc()
            return response.receive<Tilgang>().harTilgang
        } catch (e: ClientRequestException) {
            return if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.inc()
                false
            } else {
                return handleUnexpectedResponseException(e.response, resourcePerson, callId) ?: false
            }
        } catch (e: ServerResponseException) {
            return handleUnexpectedResponseException(e.response, resourcePerson, callId) ?: false
        }
    }

    private fun getTilgangskontrollUrl(personIdentNumber: PersonIdentNumber): String {
        return "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/bruker?fnr=${personIdentNumber.value}"
    }

    suspend fun hasAccessToEnhet(
        enhetNr: EnhetNr,
        token: String,
        callId: String
    ): Boolean {
        try {
            val response: HttpResponse = httpClient.get(getTilgangskontrollUrl(enhetNr)) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS.inc()
            return response.receive<Tilgang>().harTilgang
        } catch (e: ClientRequestException) {
            return if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN.inc()
                false
            } else {
                return handleUnexpectedResponseException(e.response, resourceEnhet, callId) ?: false
            }
        } catch (e: ServerResponseException) {
            COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.inc()
            return handleUnexpectedResponseException(e.response, resourceEnhet, callId) ?: false
        }
    }

    private fun getTilgangskontrollUrl(enhetNr: EnhetNr): String {
        return "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/enhet?enhet=${enhetNr.value}"
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        resource: String,
        callId: String,
    ): Boolean? {
        log.error(
            "Error while requesting access to $resource from syfo-tilgangskontroll with {} {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        when (resource) {
            resourcePerson -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.inc()
                return false
            }
            resourceEnhet -> {
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.inc()
                return false
            }
            resourcePersonList -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL.inc()
                return null
            }
        }
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        private const val resourcePerson = "PERSON"
        private const val resourcePersonList = "PERSONLIST"
        private const val resourceEnhet = "ENHET"
    }
}
