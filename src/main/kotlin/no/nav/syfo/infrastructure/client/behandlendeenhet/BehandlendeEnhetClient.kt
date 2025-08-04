package no.nav.syfo.infrastructure.client.behandlendeenhet

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.NAV_CALL_ID_HEADER
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.bearerHeader
import no.nav.syfo.api.callIdArgument
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfobehandlendeenhetClientId: String,
    syfobehandlendeenhetBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val personBehandlendeEnhetV2Url: String

    init {
        this.personBehandlendeEnhetV2Url = "$syfobehandlendeenhetBaseUrl$PERSON_V2_ENHET_PATH"
    }

    suspend fun getEnhet(
        callId: String,
        personIdent: PersonIdent,
        token: String,
    ): BehandlendeEnhetDTO? {
        val url = personBehandlendeEnhetV2Url
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfobehandlendeenhetClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_BEHANDLENDEENHET_SUCCESS.increment()
            when (response.status) {
                HttpStatusCode.OK -> response.body()
                HttpStatusCode.NoContent -> null
                else -> handleUnexpectedResponseException(response, callId)
            }
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ): BehandlendeEnhetDTO? {
        log.error(
            "Error while requesting BehandlendeEnhet of person from Syfobehandlendeenhet with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_BEHANDLENDEENHET_FAIL.increment()
        return null
    }

    companion object {
        const val PERSON_V2_ENHET_PATH = "/api/internad/v2/personident"
        private val log = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
