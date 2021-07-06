package no.nav.syfo.client.narmesteleder

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

data class NarmesteLederRelasjonDTO(
    val narmesteLederRelasjon: NarmesteLederDTO
)

class NarmesteLederClient(
    narmesteLederBaseUrl: String,
    private val narmestelederClientId: String,
    private val azureAdV2Client: AzureAdV2Client
) {
    private val sykmeldtNarmesteLederePath = "$narmesteLederBaseUrl/sykmeldt/narmesteledere?utvidet=ja"
    private val sykmeldtAktivNarmesteLederPath = "$narmesteLederBaseUrl/sykmeldt/narmesteleder?orgnummer="

    private val httpClient = httpClientDefault()

    suspend fun activeLeader(
        personIdentNumber: PersonIdentNumber,
        virksomhetsnummer: Virksomhetsnummer,
        callId: String
    ): NarmesteLederDTO? {

        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = narmestelederClientId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request access to Narmesteleder: Failed to get System token")

        try {
            val narmesteLederRelasjon: NarmesteLederRelasjonDTO =
                httpClient.get("$sykmeldtAktivNarmesteLederPath${virksomhetsnummer.value}") {
                    header(HttpHeaders.Authorization, bearerHeader(systemToken))
                    header("Sykmeldt-Fnr", personIdentNumber.value)
                    accept(ContentType.Application.Json)
                }

            return narmesteLederRelasjon.narmesteLederRelasjon
        } catch (ex: ClientRequestException) {
            log.error(
                "Error while requesting NarmesteLedere of person from $sykmeldtAktivNarmesteLederPath${virksomhetsnummer.value} with {}, {}",
                StructuredArguments.keyValue("statusCode", ex.response.status.value.toString()),
                callIdArgument(callId)
            )
            return null
        }
    }

    suspend fun narmesteLedere(
        personIdentNumber: PersonIdentNumber,
        callId: String
    ): List<NarmesteLederDTO> {

        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = narmestelederClientId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request access to narmesteleder: Failed to get System token")

        try {
            return httpClient.get(sykmeldtNarmesteLederePath) {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                header("Sykmeldt-Fnr", personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
        } catch (ex: ClientRequestException) {
            log.error(
                "Error while requesting NarmesteLedere of person from $sykmeldtNarmesteLederePath with {}, {}",
                StructuredArguments.keyValue("statusCode", ex.response.status.value.toString()),
                callIdArgument(callId)
            )
            return emptyList()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
    }
}
