package no.nav.syfo.client.behandlendeenhet

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    syfobehandlendeenhetBaseUrl: String
) {
    private val personBehandlendeEnhetUrl: String

    init {
        this.personBehandlendeEnhetUrl = "$syfobehandlendeenhetBaseUrl$PERSON_ENHET_PATH"
    }

    private val httpClient = httpClientDefault()

    suspend fun getEnhet(
        callId: String,
        personIdentNumber: PersonIdentNumber,
        token: String
    ): BehandlendeEnhetDTO? {
        return try {
            val response: HttpResponse = httpClient.get(personBehandlendeEnhetUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_BEHANDLENDEENHET_SUCCESS.inc()
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
    ): BehandlendeEnhetDTO? {
        log.error(
            "Error while requesting BehandlendeEnhet of person from Syfobehandlendeenhet with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
        return null
    }

    companion object {
        const val PERSON_ENHET_PATH = "/api/internad/personident"
        private val log = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
