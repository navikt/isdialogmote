package no.nav.syfo.infrastructure.client.motebehov

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.bearerHeader
import no.nav.syfo.application.client.IMotebehovClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.motebehov.Tilbakemelding
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.metric.COUNT_CALL_MOTEBEHOV_BEHANDLE_FAIL
import no.nav.syfo.metric.COUNT_CALL_MOTEBEHOV_BEHANDLE_SUCCESS
import no.nav.syfo.metric.COUNT_CALL_MOTEBEHOV_TILBAKEMELDING_FAIL
import no.nav.syfo.metric.COUNT_CALL_MOTEBEHOV_TILBAKEMELDING_SUCCESS
import org.slf4j.LoggerFactory

class MotebehovClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfomotebehovClientId: String,
    syfomotebehovBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault(),
) : IMotebehovClient {
    private val behandleUrl = "$syfomotebehovBaseUrl$MOTEBEHOV_BEHANDLE_PATH"
    private val tilbakemeldingUrl = "$syfomotebehovBaseUrl$MOTEBEHOV_TILBAKEMELDING_PATH"

    override suspend fun behandleMotebehov(
        personIdent: PersonIdent,
        token: String,
    ) {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfomotebehovClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to behandle motebehov: Failed to get OBO token")

        try {
            httpClient.post(behandleUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_MOTEBEHOV_BEHANDLE_SUCCESS.increment()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, "behandle")
            throw e
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, "behandle")
            throw e
        }
    }

    override suspend fun sendTilbakemelding(
        tilbakemelding: Tilbakemelding,
        token: String,
    ) {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfomotebehovClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to send tilbakemelding: Failed to get OBO token")

        try {
            httpClient.post(tilbakemeldingUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(tilbakemelding)
            }
            COUNT_CALL_MOTEBEHOV_TILBAKEMELDING_SUCCESS.increment()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, "tilbakemelding")
            throw e
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, "tilbakemelding")
            throw e
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        resource: String,
    ) {
        log.error(
            "Error while calling syfomotebehov $resource with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
        )
        when (resource) {
            "behandle" -> COUNT_CALL_MOTEBEHOV_BEHANDLE_FAIL.increment()
            "tilbakemelding" -> COUNT_CALL_MOTEBEHOV_TILBAKEMELDING_FAIL.increment()
        }
    }

    companion object {
        private const val MOTEBEHOV_BASE_PATH = "/api/internad/v4/veileder"
        const val MOTEBEHOV_BEHANDLE_PATH = "$MOTEBEHOV_BASE_PATH/motebehov/behandle"
        const val MOTEBEHOV_TILBAKEMELDING_PATH = "$MOTEBEHOV_BASE_PATH/motebehov/tilbakemelding"

        private val log = LoggerFactory.getLogger(MotebehovClient::class.java)
    }
}
