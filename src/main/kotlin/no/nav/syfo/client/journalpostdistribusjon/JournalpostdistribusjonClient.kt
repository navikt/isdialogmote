package no.nav.syfo.client.journalpostdistribusjon

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class JournalpostdistribusjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val dokdistFordelingClientId: String,
    dokdistFordelingBaseUrl: String
) {

    private val distribuerJournalpostUrl: String = "$dokdistFordelingBaseUrl$DISTRIBUER_JOURNALPOST_PATH"
    private val httpClient = httpClientDefault()

    suspend fun distribuerJournalpost(
        journalpostId: String
    ): JournalpostdistribusjonResponse? {
        val accessToken = azureAdV2Client.getSystemToken(dokdistFordelingClientId)?.accessToken
            ?: throw RuntimeException("Failed to request Journalpost distribution: Failed to get System token")
        val request = JournalpostdistribusjonRequest(
            journalpostId = journalpostId,
            bestillendeFagsystem = BESTILLENDE_FAGSYSTEM,
            dokumentProdApp = DOKUMENTPRODUSERENDE_APP
        )
        return try {
            val response = httpClient.post(distribuerJournalpostUrl) {
                header(HttpHeaders.Authorization, bearerHeader(accessToken))
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<JournalpostdistribusjonResponse>()
            COUNT_CALL_JOURNALPOSTDISTRIBUSJON_SUCCESS.increment()
            response
        } catch (e: ClientRequestException) {
            // HttpStatusCode.Gone brukes når mottaker er død og dødsbo har ukjent adresse
            if (e.response.status != HttpStatusCode.Gone) {
                throw getUnexpectedResponseException(e.response, e.message)
            } else null
        } catch (e: ServerResponseException) {
            throw getUnexpectedResponseException(e.response, e.message)
        }
    }

    private fun getUnexpectedResponseException(
        response: HttpResponse,
        message: String?
    ): RuntimeException {
        COUNT_CALL_JOURNALPOSTDISTRIBUSJON_FAIL.increment()
        return RuntimeException(
            "Error while requesting Journalpost distribution from dokdistFordeling with status ${response.status.value} and message $message"
        )
    }

    companion object {
        const val BESTILLENDE_FAGSYSTEM = "MODIA_SYKEFRAVAER"
        const val DOKUMENTPRODUSERENDE_APP = "isdialogmote"
        const val DISTRIBUER_JOURNALPOST_PATH = "/rest/v1/distribuerjournalpost"
        private val log = LoggerFactory.getLogger(JournalpostdistribusjonClient::class.java)
    }
}
