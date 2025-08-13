package no.nav.syfo.infrastructure.client.dialogmelding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import no.nav.syfo.api.bearerHeader
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import org.slf4j.LoggerFactory
import java.util.UUID

class DialogmeldingClient(
    private val url: String,
    private val clientId: String,
    private val azureAdClient: AzureAdV2Client,
    private val client: HttpClient = httpClientDefault(),
) {
    private val log = LoggerFactory.getLogger(DialogmeldingClient::class.qualifiedName)

    suspend fun getBehandler(
        behandlerRef: UUID,
    ): BehandlerDTO? {
        val token = azureAdClient.getSystemToken(clientId)?.accessToken
            ?: throw RuntimeException("Failed to get behandler: No token was found")
        val requestUrl = "$url/$GET_BEHANDLER_PATH/$behandlerRef"

        val response = try {
            client.get(requestUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, bearerHeader(token))
                    accept(ContentType.Application.Json)
                }
            }
        } catch (e: Exception) {
            log.error("Exception while getting behandler", e)
            throw e
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.body()
            }
            else -> {
                null
            }
        }
    }

    companion object {
        private const val GET_BEHANDLER_PATH = "api/v1/behandler"
    }
}
