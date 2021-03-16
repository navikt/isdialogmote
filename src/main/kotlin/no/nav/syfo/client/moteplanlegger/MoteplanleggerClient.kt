package no.nav.syfo.client.moteplanlegger

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.moteplanlegger.domain.PlanlagtMoteDTO
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.util.*

class MoteplanleggerClient(
    syfomoteadminBaseUrl: String
) {
    private val planlagtMoteUrl: String

    init {
        this.planlagtMoteUrl = "$syfomoteadminBaseUrl$PLANLAGTMOTE_PATH"
    }

    private val httpClient = httpClientDefault()

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
            handleUnexpectedResponseException(e.response, callId)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting PlanlagtMote from Syfomoteadmin with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
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
                handleUnexpectedResponseExceptionBekreft(response, callId)
                false
            }
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseExceptionBekreft(e.response, callId)
            false
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseExceptionBekreft(e.response, callId)
            false
        }
    }

    private fun handleUnexpectedResponseExceptionBekreft(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting to Bekreft PlanlagtMote in Syfomoteadmin with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_MOTEADMIN_BEKREFT_FAIL.inc()
    }

    companion object {
        const val PLANLAGTMOTE_PATH = "/syfomoteadmin/api/internad/moter"
        const val PLANLAGTMOTE_BEKREFT_PATH = "/bekreft"
        private val log = LoggerFactory.getLogger(MoteplanleggerClient::class.java)
    }
}
