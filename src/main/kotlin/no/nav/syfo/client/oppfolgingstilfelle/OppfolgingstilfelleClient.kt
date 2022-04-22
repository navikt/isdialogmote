package no.nav.syfo.client.oppfolgingstilfelle

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class OppfolgingstilfelleClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val isoppfolgingstilfelleClientId: String,
    isoppfolgingstilfelleBaseUrl: String,
) {
    private val personOppfolgingstilfelleUrl: String =
        "$isoppfolgingstilfelleBaseUrl$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH"

    private val httpClient = httpClientDefault()

    suspend fun oppfolgingstilfelle(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Oppfolgingstilfelle? {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = isoppfolgingstilfelleClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")
        return try {
            val response: HttpResponse = httpClient.get(personOppfolgingstilfelleUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            val oppfolgingstilfellePerson = response.body<OppfolgingstilfellePersonDTO>()
                .toLatestOppfolgingstilfelle()
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS.increment()
            oppfolgingstilfellePerson
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting OppfolgingstilfellePerson from Isoppfolgingstilfelle with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                callIdArgument(callId),
            )
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL.increment()
            null
        }
    }

    companion object {
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH =
            "/api/internad/v1/oppfolgingstilfelle/personident"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
    }
}
