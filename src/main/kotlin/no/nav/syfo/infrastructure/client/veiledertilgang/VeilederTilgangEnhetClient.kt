package no.nav.syfo.infrastructure.client.veiledertilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import no.nav.syfo.api.NAV_CALL_ID_HEADER
import no.nav.syfo.api.bearerHeader
import no.nav.syfo.azure.AzureAdClient
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.metric.*
import org.slf4j.LoggerFactory
import java.time.Duration

class VeilederTilgangEnhetClient(
    private val azureAdClient: AzureAdClient,
    private val tilgangskontrollClientId: String,
    tilgangskontrollBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val tilgangskontrollEnhetUrl = "$tilgangskontrollBaseUrl$TILGANGSKONTROLL_ENHET_PATH"

    suspend fun hasAccessToEnhet(
        enhetNr: EnhetNr,
        token: String,
        callId: String,
    ): Boolean {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = tilgangskontrollClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")

        val url = "$tilgangskontrollEnhetUrl/${enhetNr.value}"
        val starttime = System.currentTimeMillis()
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS.increment()
            response.body<EnhetTilgang>().erGodkjent
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, callId)
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.increment()
            }
            false
        } catch (e: ServerResponseException) {
            COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.increment()
            handleUnexpectedResponseException(e.response, callId)
            false
        } catch (e: ClosedReceiveChannelException) {
            COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.increment()
            throw RuntimeException("Caught ClosedReceiveChannelException in hasAccessToEnhet", e)
        } finally {
            val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
            HISTOGRAM_CALL_TILGANGSKONTROLL_ENHET_TIMER.record(duration)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting access to enhet from istilgangskontroll: statusCode={}, callId={}",
            response.status.value,
            callId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangEnhetClient::class.java)

        const val TILGANGSKONTROLL_ENHET_PATH = "/api/tilgang/navident/enhet"
    }
}

internal data class EnhetTilgang(
    val erGodkjent: Boolean,
)
