package no.nav.syfo.client.dokarkiv

import io.ktor.client.call.*
import io.ktor.client.features.*
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
                    body = journalpostRequest
                }
                val journalpostResponse = response.receive<JournalpostResponse>()
                COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS.increment()
                journalpostResponse
            } catch (e: ClientRequestException) {
                handleUnexpectedResponseException(e.response, e.message)
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
