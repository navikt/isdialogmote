package no.nav.syfo.client.journalpostdistribusjon

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class JournalpostdistribusjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val isproxyClientId: String,
    isproxyUrl: String
) {

    private val distribuerJournalpostUrl: String = "$isproxyUrl$DISTRIBUER_JOURNALPOST_PATH"
    private val httpClient = httpClientDefault()

    suspend fun distribuerJournalpost(
        journalpostId: String
    ): JournalpostdistribusjonResponse? {
        val accessToken = azureAdV2Client.getSystemToken(isproxyClientId)?.accessToken
            ?: throw RuntimeException("Failed to request Journalpost distribution: No accessToken was found")
        val request = JournalpostdistribusjonRequest(
            journalpostId = journalpostId,
            bestillendeFagsystem = BESTILLENDE_FAGSYSTEM,
            dokumentProdApp = DOKUMENTPRODUSERENDE_APP
        )
        try {
            val response = httpClient.post<JournalpostdistribusjonResponse>(distribuerJournalpostUrl) {
                header(HttpHeaders.Authorization, bearerHeader(accessToken))
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = request
            }
            COUNT_CALL_JOURNALPOSTDISTRIBUSJON_SUCCESS.increment()
            return response
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, e.message)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, e.message)
        }
        return null
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        message: String?
    ) {
        log.error(
            "Error while requesting Journalpost distribution from isproxy with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("message", message),
        )
        COUNT_CALL_JOURNALPOSTDISTRIBUSJON_FAIL.increment()
    }

    companion object {
        const val BESTILLENDE_FAGSYSTEM = "MODIA_SYKEFRAVAER"
        const val DOKUMENTPRODUSERENDE_APP = "isdialogmote"
        const val DISTRIBUER_JOURNALPOST_PATH = "/api/v1/distribuerJournalpost"
        private val log = LoggerFactory.getLogger(JournalpostdistribusjonClient::class.java)
    }
}
