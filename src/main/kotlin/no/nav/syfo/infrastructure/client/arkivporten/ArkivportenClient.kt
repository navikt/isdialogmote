package no.nav.syfo.infrastructure.client.arkivporten

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import no.nav.syfo.api.NAV_CALL_ID_HEADER
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import org.slf4j.LoggerFactory

class ArkivportenClient(
    private val baseUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val scopeClientId: String,
    private val client: HttpClient = httpClientDefault(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun sendDocument(
        document: ArkivportenDocumentRequestDTO,
        token: String,
        callId: String,
    ) {
        val token = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = scopeClientId,
            token = token
        )?.accessToken ?: throw ArkivportenClientException("$GENERIC_ERROR_MESSAGE: No token was found")
        val requestUrl = "$baseUrl/$ARKIVPORTEN_DOCUMENT_PATH"

        try {
            val response =  client.post(requestUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                    bearerAuth(token)
                    header(NAV_CALL_ID_HEADER, callId)
                }
                setBody(document)
            }
            if (!response.status.isSuccess()) {
                throw ArkivportenClientException(
                    "Received status code ${response.status}"
                )
            }
        } catch (e: ResponseException) {
            log.error("$GENERIC_ERROR_MESSAGE: Error in response ${document.documentId}", e)
        } catch (e: Exception) {
            log.error("$GENERIC_ERROR_MESSAGE: ${document.documentId}.", e)
        }
    }

    companion object {
        const val ARKIVPORTEN_DOCUMENT_PATH = "/internal/api/v1/documents"
        private const val GENERIC_ERROR_MESSAGE = "Error sending document to Arkivporten"
    }
}

class ArkivportenClientException(message: String) : RuntimeException(message)
