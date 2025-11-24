package no.nav.syfo.infrastructure.client.arkivporten

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import org.slf4j.LoggerFactory

private const val GENERIC_ERROR_MESSAGE = "Error sending document to Arkivporten"

class ArkivportenClient(
    private val baseUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val clientId: String,
    private val client: HttpClient = httpClientDefault(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun sendDocument(
        document: ArkivportenDocument,
    ) {
        val token = azureAdV2Client.getSystemToken(clientId)?.accessToken
            ?: throw ArkivportenClientException("$GENERIC_ERROR_MESSAGE: No token was found")
        val requestUrl = "$baseUrl/$ARKIVPORTEN_DOCUMENT_PATH"

        val response = try {
            client.post(requestUrl) {
                headers {
                    contentType(ContentType.Application.Json)
                    bearerAuth(token)
                }
                setBody(document)
            }
        } catch (e: ResponseException) {
            log.error(GENERIC_ERROR_MESSAGE, e)
            throw ArkivportenClientException(GENERIC_ERROR_MESSAGE, e)
        }

        if (!response.status.isSuccess()) {
            throw ArkivportenClientException(
                "$GENERIC_ERROR_MESSAGE: Received status code ${response.status}"
            )
        }
    }

    companion object {
        const val ARKIVPORTEN_DOCUMENT_PATH = "/internal/api/v1/documents"
    }
}

class ArkivportenClientException(message: String) : RuntimeException(message) {
    constructor(message: String, cause: Throwable) : this(message) {
        initCause(cause)
    }
}
