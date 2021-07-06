package no.nav.syfo.client.narmesteleder

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

data class NarmesteLederRelasjonDTO(
    val narmesteLederRelasjon: NarmesteLederDTO
)

class NarmesteLederClient(
    narmesteLederBaseUrl: String,
    private val narmestelederClientId: String,
    private val azureAdV2Client: AzureAdV2Client
) {
    private val sykmeldtAktivNarmesteLederPath = "$narmesteLederBaseUrl$NARMESTELEDER_CURRENT_PATH?orgnummer="

    private val httpClient = httpClientDefault()

    suspend fun activeLeader(
        personIdentNumber: PersonIdentNumber,
        virksomhetsnummer: Virksomhetsnummer,
        callId: String
    ): NarmesteLederDTO? {

        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = narmestelederClientId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request access to current Narmesteleder: Failed to get System token")

        return try {
            val url = "$sykmeldtAktivNarmesteLederPath${virksomhetsnummer.value}"
            val narmesteLederRelasjon: NarmesteLederRelasjonDTO =
                httpClient.get(url) {
                    header(HttpHeaders.Authorization, bearerHeader(systemToken))
                    header("Sykmeldt-Fnr", personIdentNumber.value)
                    accept(ContentType.Application.Json)
                }
            COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS.inc()
            narmesteLederRelasjon.narmesteLederRelasjon
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting current NarmesteLeder of person from Narmesteleder with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_FAIL.inc()
    }

    companion object {
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
        const val NARMESTELEDER_CURRENT_PATH = "/sykmeldt/narmesteleder"
    }
}
