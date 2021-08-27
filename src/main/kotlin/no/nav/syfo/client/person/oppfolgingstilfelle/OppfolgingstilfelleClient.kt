package no.nav.syfo.client.person.oppfolgingstilfelle

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class OppfolgingstilfelleClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfopersonClientId: String,
    syfopersonBaseUrl: String
) {
    private val personOppfolgingstilfelleV2Url: String = "$syfopersonBaseUrl$PERSON_V2_OPPFOLGINGSTILFELLE_PATH"

    private val httpClient = httpClientDefault()

    suspend fun oppfolgingstilfelle(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): OppfolgingstilfellePerson? {
        val url = personOppfolgingstilfelleV2Url
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfopersonClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            val oppfolgingstilfelle = response.receive<OppfolgingstilfellePersonDTO>()
                .toOppfolgingstilfelle()
            COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS.increment()
            oppfolgingstilfelle
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
            "Error while requesting Oppfolgingstilfelle of person from Syfoperson with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId),
        )
        COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL.increment()
    }

    companion object {
        const val PERSON_V2_PATH = "/syfoperson/api/v2/person"
        const val PERSON_V2_OPPFOLGINGSTILFELLE_PATH = "$PERSON_V2_PATH/oppfolgingstilfelle"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
    }
}
