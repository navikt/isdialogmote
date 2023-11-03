package no.nav.syfo.client.veiledertilgang

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.time.Duration

class VeilederTilgangskontrollClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val tilgangskontrollClientId: String,
    tilgangskontrollBaseUrl: String,
) {
    private val httpClient = httpClientDefault()

    private val tilgangskontrollPersonListUrl: String
    private val tilgangskontrollEnhetUrl: String

    init {
        tilgangskontrollPersonListUrl = "$tilgangskontrollBaseUrl$TILGANGSKONTROLL_PERSON_LIST_PATH"
        tilgangskontrollEnhetUrl = "$tilgangskontrollBaseUrl$TILGANGSKONTROLL_ENHET_PATH"
    }

    suspend fun hasAccessToPersonList(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): List<PersonIdent> {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = tilgangskontrollClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        val starttime = System.currentTimeMillis()
        return try {
            val personIdentStringList = personIdentList.map { it.value }

            val response: HttpResponse = httpClient.post(urlString = tilgangskontrollPersonListUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token = oboToken))
                header(NAV_CALL_ID_HEADER, value = callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(personIdentStringList)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS.increment()
            response.body<List<String>>().map { personIdent -> PersonIdent(personIdent) }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, resourcePersonList, callId = callId)
            }
            emptyList()
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, resourcePersonList, callId = callId)
            emptyList()
        } catch (e: ClosedReceiveChannelException) {
            handleClosedReceiveChannelException(e, "hasAccessToPersonList", resourcePersonList)
            emptyList()
        } finally {
            val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
            HISTOGRAM_CALL_TILGANGSKONTROLL_PERSONS_TIMER.record(duration)
        }
    }

    suspend fun hasAccessToEnhet(
        enhetNr: EnhetNr,
        token: String,
        callId: String
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
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
            response.body<Tilgang>().erGodkjent
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, resourceEnhet, callId)
            }
            false
        } catch (e: ServerResponseException) {
            COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.increment()
            handleUnexpectedResponseException(e.response, resourceEnhet, callId)
            false
        } catch (e: ClosedReceiveChannelException) {
            handleClosedReceiveChannelException(e, "hasAccessToEnhet", resourceEnhet)
            false
        } finally {
            val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
            HISTOGRAM_CALL_TILGANGSKONTROLL_ENHET_TIMER.record(duration)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        resource: String,
        callId: String,
    ) {
        log.error(
            "Error while requesting access to $resource from istilgangskontroll with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        incrementFailCounter(resource)
    }

    private fun incrementFailCounter(resource: String) {
        when (resource) {
            resourceEnhet -> {
                COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL.increment()
            }

            resourcePersonList -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL.increment()
            }
        }
    }

    private fun handleClosedReceiveChannelException(
        e: ClosedReceiveChannelException,
        failingFunction: String,
        resource: String,
    ) {
        incrementFailCounter(resource)
        throw RuntimeException("Caught ClosedReceiveChannelException in $failingFunction", e)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        private const val resourcePersonList = "PERSONLIST"
        private const val resourceEnhet = "ENHET"

        const val TILGANGSKONTROLL_COMMON_PATH = "/api/tilgang/navident"
        const val TILGANGSKONTROLL_PERSON_LIST_PATH = "$TILGANGSKONTROLL_COMMON_PATH/brukere"
        const val TILGANGSKONTROLL_ENHET_PATH = "$TILGANGSKONTROLL_COMMON_PATH/enhet"
    }
}
