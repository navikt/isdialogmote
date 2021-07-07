package no.nav.syfo.client.narmesteleder

import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
    private val sykmeldtAktivNarmesteLederPath = "$narmesteLederBaseUrl$CURRENT_NARMESTELEDER_PATH?orgnummer="
    private val ansatteNarmesteLederPath = "$narmesteLederBaseUrl$CURRENT_ANSATTE_PATH"

    private val httpClient = httpClientDefault()

    suspend fun activeLeder(
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
                    header(SYKMELDT_FNR_HEADER, personIdentNumber.value)
                    accept(ContentType.Application.Json)
                }
            COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS.increment()
            narmesteLederRelasjon.narmesteLederRelasjon
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        }
    }

    suspend fun getAktiveAnsatte(narmesteLederIdent: PersonIdentNumber, callId: String): List<NarmesteLederDTO> {
        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = narmestelederClientId,
        )?.accessToken
            ?: throw RuntimeException("Could not get AktiveAnsatte: Failed to get System token")

        return try {
            val narmesteLedere: List<NarmesteLederDTO> =
                httpClient.get(ansatteNarmesteLederPath) {
                    header(HttpHeaders.Authorization, bearerHeader(systemToken))
                    header(NARMESTELEDER_FNR_HEADER, narmesteLederIdent.value)
                    accept(ContentType.Application.Json)
                }
            COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS.increment()
            narmesteLedere
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
            emptyList()
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            emptyList()
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
        COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_FAIL.increment()
    }

    companion object {
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
        const val CURRENT_NARMESTELEDER_PATH = "/sykmeldt/narmesteleder"
        const val NARMESTELEDERE_PATH = "/sykmeldt/narmesteledere"
        const val CURRENT_ANSATTE_PATH = "/leder/narmesteleder/aktive"
        const val SYKMELDT_FNR_HEADER = "Sykmeldt-Fnr"
        const val NARMESTELEDER_FNR_HEADER = "Narmeste-Leder-Fnr"
    }
}
