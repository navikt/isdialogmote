package no.nav.syfo.client.moteplanlegger

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
import no.nav.syfo.client.moteplanlegger.domain.PlanlagtMoteDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory
import java.util.*

class MoteplanleggerClient(
    syfomoteadminBaseUrl: String
) {
    private val planlagtMoteUrl: String

    init {
        this.planlagtMoteUrl = "$syfomoteadminBaseUrl$PLANLAGTMOTE_PATH"
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

    suspend fun planlagtMote(
        planlagtMoteUUID: UUID,
        token: String,
        callId: String
    ): PlanlagtMoteDTO? {
        return try {
            val response: HttpResponse = httpClient.get("$planlagtMoteUrl/$planlagtMoteUUID") {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_MOTEADMIN_BASE_SUCCESS.inc()
            response.receive<PlanlagtMoteDTO>()
        } catch (e: ClientRequestException) {
            handleUnexpectedReponseException(e.response)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedReponseException(e.response)
            null
        }
    }

    private fun handleUnexpectedReponseException(response: HttpResponse) {
        log.error(
            "Error while requesting PlanlagtMote from Syfomoteadmin with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString())
        )
        COUNT_CALL_MOTEADMIN_BASE_FAIL.inc()
    }

    suspend fun bekreftPlanlagtMote(
        planlagtMoteUUID: UUID,
        token: String,
        callId: String
    ): Boolean {
        return try {
            val response: HttpResponse = httpClient.post("$planlagtMoteUrl/$planlagtMoteUUID$PLANLAGTMOTE_BEKREFT_PATH?varsle=false") {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                COUNT_CALL_MOTEADMIN_BEKREFT_SUCCESS.inc()
                true
            } else {
                handleUnexpectedReponseExceptionBekreft(response)
                false
            }
        } catch (e: ClientRequestException) {
            handleUnexpectedReponseExceptionBekreft(e.response)
            false
        } catch (e: ServerResponseException) {
            handleUnexpectedReponseExceptionBekreft(e.response)
            false
        }
    }

    private fun handleUnexpectedReponseExceptionBekreft(response: HttpResponse) {
        log.error(
            "Error while requesting to Bekreft PlanlagtMote in Syfomoteadmin with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString())
        )
        COUNT_CALL_MOTEADMIN_BEKREFT_FAIL.inc()
    }

    companion object {
        const val PLANLAGTMOTE_PATH = "/syfomoteadmin/api/internad/moter"
        const val PLANLAGTMOTE_BEKREFT_PATH = "/bekreft"
        private val log = LoggerFactory.getLogger(MoteplanleggerClient::class.java)
    }
}