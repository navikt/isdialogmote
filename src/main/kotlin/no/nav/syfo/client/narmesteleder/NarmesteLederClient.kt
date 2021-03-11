package no.nav.syfo.client.narmesteleder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class NarmesteLederClient(
    modiasyforestBaseUrl: String
) {
    private val personNarmesteLederUrl: String

    init {
        this.personNarmesteLederUrl = "$modiasyforestBaseUrl$PERSON_NARMESTELEDER_PATH"
    }

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun activeLeader(
        personIdentNumber: PersonIdentNumber,
        virksomhetsnummer: Virksomhetsnummer,
        token: String,
        callId: String
    ): NarmesteLederDTO? {
        return narmesteLedere(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId
        ).filter {
            it.orgnummer == virksomhetsnummer.value
        }.maxByOrNull {
            it.fomDato
        }
    }

    suspend fun narmesteLedere(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String
    ): List<NarmesteLederDTO> {
        return try {
            val response: HttpResponse = httpClient.get(personNarmesteLederUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_SUCCESS.inc()
            response.receive()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ): List<NarmesteLederDTO> {
        log.error(
            "Error while requesting NarmesteLedere of person from Modiasyforest with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_FAIL.inc()
        return emptyList()
    }

    companion object {
        const val PERSON_NARMESTELEDER_PATH = "/modiasyforest/api/internad/allnaermesteledere"
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
    }
}
