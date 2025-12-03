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
import no.nav.syfo.api.callIdArgument
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import org.slf4j.LoggerFactory

/**
 * A client for sending documents to Arkivporten, which is a document archiving service using
 * Digdirs Dialogporten for outbound communication/notifications.
 *
 * @param baseUrl The base URL of the Arkivporten service
 * @param azureAdV2Client An instance of AzureAdV2Client for obtaining the OBO-token used for authentication
 * @param scopeClientId The client ID used for obtaining the on-behalf-of token
 * @param client An instance of HttpClient for making HTTP requests (default is httpClientDefault)
 * */
class ArkivportenClient(
    private val baseUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val scopeClientId: String,
    private val client: HttpClient = httpClientDefault(),
) {

    /**
     * Sends a document to Arkivporten
     *
     * @param document The document to be sent, encapsulated in an ArkivportenDocumentRequestDTO
     * @param token The token used for authentication, which will be exchanged for an OBO token
     * @param callId A unique identifier for the call, used for tracing and logging
     *
     * @exception ArkivportenClientException if there is an error during the sending process
     * */
    suspend fun sendDocument(
        document: ArkivportenDocumentRequestDTO,
        token: String,
        callId: String,
    ) {
        val token = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = scopeClientId,
            token = token
        )?.accessToken ?: throw ArkivportenClientException(
            "No token was found",
            document.documentId.toString()
        )
        val requestUrl = "$baseUrl/$ARKIVPORTEN_DOCUMENT_PATH"

        runCatching {
            val res = client.post(requestUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                    bearerAuth(token)
                    header(NAV_CALL_ID_HEADER, callId)
                }
                setBody(document)
            }
            if (!res.status.isSuccess()) {
                throw ArkivportenClientException(
                    "Received status code ${res.status.value}",
                    document.documentId.toString(),
                )
            }
        }.getOrElse { e ->
            logger.error(
                e.message,
                callIdArgument(callId),
                e
            )
            when(e) {
                is ArkivportenClientException -> throw e
                is ResponseException -> throw ArkivportenClientException(
                    "Received status code ${e.response.status.value}",
                    document.documentId.toString(),
                    e
                )
                else ->  throw ArkivportenClientException(
                    "Failed to send document to Arkivporten: ${e.message}",
                    document.documentId.toString(),
                    e
                )
            }
        }
    }

    companion object {
        const val ARKIVPORTEN_DOCUMENT_PATH = "/internal/api/v1/documents"
        private val logger = LoggerFactory.getLogger(ArkivportenClient::class.java)
    }

    class ArkivportenClientException(message: String, val documentId: String) :
        RuntimeException("Error sending document to Arkivporten: $message, documentId: $documentId") {
        constructor(message: String, documentId: String, cause: Throwable) : this(message, documentId) {
            initCause(cause)
        }
    }
}
