package no.nav.syfo.client.dokarkiv

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class DokarkivClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val dokarkivClientId: String,
    dokarkivBaseUrl: String,
) {
    private val journalpostUrl: String = "$dokarkivBaseUrl$JOURNALPOST_PATH"

    private val httpClient = httpClientDefault()

    suspend fun journalfor(
        journalpostRequest: JournalpostRequest,
    ): JournalpostResponse? {
        val accessToken = azureAdV2Client.getSystemToken(dokarkivClientId)?.accessToken
        accessToken?.let { token ->
            return try {
                val response: HttpResponse = httpClient.post(journalpostUrl) {
                    parameter(JOURNALPOST_PARAM_STRING, JOURNALPOST_PARAM_VALUE)
                    header(HttpHeaders.Authorization, bearerHeader(token))
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    setBody(journalpostRequest)
                }
                val journalpostResponse = response.body<JournalpostResponse>()
                COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS.increment()
                if (journalpostResponse.journalpostferdigstilt == null || !journalpostResponse.journalpostferdigstilt) {
                    log.error("Journalpost med id ${journalpostResponse.journalpostId} ble ikke ferdigstilt (status er ${journalpostResponse.journalstatus})")
                }
                journalpostResponse
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Conflict) {
                    val journalpostResponse = e.response.body<JournalpostResponse>()
                    log.warn("Journalpost med id ${journalpostResponse.journalpostId} lagret fra før (409 Conflict)")
                    COUNT_CALL_DOKARKIV_JOURNALPOST_CONFLICT.increment()
                    journalpostResponse
                } else {
                    handleUnexpectedResponseException(e.response, e.message)
                }
            } catch (e: ServerResponseException) {
                handleUnexpectedResponseException(e.response, e.message)
            }
        } ?: throw RuntimeException("Failed to Journalfor Journalpost: No accessToken was found")
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        message: String?,
    ): JournalpostResponse? {
        log.error(
            "Error while requesting Dokarkiv to Journalpost PDF with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("message", message),
        )
        COUNT_CALL_DOKARKIV_JOURNALPOST_FAIL.increment()
        return null
    }

    companion object {
        const val JOURNALPOST_PATH = "/rest/journalpostapi/v1/journalpost"
        private const val JOURNALPOST_PARAM_STRING = "forsoekFerdigstill"
        private const val JOURNALPOST_PARAM_VALUE = true
        private val log = LoggerFactory.getLogger(DokarkivClient::class.java)
    }
}
