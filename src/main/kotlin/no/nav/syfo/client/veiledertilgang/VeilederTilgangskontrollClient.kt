package no.nav.syfo.client.veiledertilgang

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfotilgangskontrollClientId: String,
    private val tilgangskontrollBaseUrl: String,
) {
    private val httpClient = httpClientDefault()

    suspend fun hasAccessToPersonList(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String,
    ): List<PersonIdentNumber> {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfotilgangskontrollClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        return try {
            val personIdentStringList = personIdentNumberList.map { it.value }
            val url = getTilgangskontrollUrl()

            val response: HttpResponse = httpClient.post(urlString = url) {
                header(HttpHeaders.Authorization, bearerHeader(token = oboToken))
                header(NAV_CALL_ID_HEADER, value = callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = personIdentStringList
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS.increment()
            response.receive<List<String>>().map { personIdent -> PersonIdentNumber(personIdent) }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, resourceEnhet, callId = callId)
            }
            emptyList()
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, resourceEnhet, callId = callId)
            emptyList()
        } catch (e: ClosedReceiveChannelException) {
            handleClosedReceiveChannelException(e, "hasAccessToPersonList", resourceEnhet)
            emptyList()
        }
    }

    private fun getTilgangskontrollUrl(): String {
        return "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/navident/brukere"
    }

    suspend fun hasAccess(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfotilgangskontrollClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        val url = getTilgangskontrollV2Url(personIdentNumber)
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            response.receive<Tilgang>().harTilgang
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, resourcePerson, callId)
            }
            false
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, resourcePerson, callId)
            false
        } catch (e: ClosedReceiveChannelException) {
            handleClosedReceiveChannelException(e, "hasAccess", resourcePerson)
            false
        }
    }

    private fun getTilgangskontrollV2Url(personIdentNumber: PersonIdentNumber): String {
        return "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/navident/bruker/${personIdentNumber.value}"
    }

    suspend fun hasAccessToEnhet(
        enhetNr: EnhetNr,
        token: String,
        callId: String
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfotilgangskontrollClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")

        val url = getTilgangskontrollUrl(enhetNr)
        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS.increment()
            response.receive<Tilgang>().harTilgang
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
        }
    }

    private fun getTilgangskontrollUrl(enhetNr: EnhetNr) =
        "$tilgangskontrollBaseUrl/syfo-tilgangskontroll/api/tilgang/navident/enhet/${enhetNr.value}"

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        resource: String,
        callId: String,
    ) {
        log.error(
            "Error while requesting access to $resource from syfo-tilgangskontroll with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        incrementFailCounter(resource)
    }

    private fun incrementFailCounter(resource: String) {
        when (resource) {
            resourcePerson -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.increment()
            }
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

        private const val resourcePerson = "PERSON"
        private const val resourcePersonList = "PERSONLIST"
        private const val resourceEnhet = "ENHET"
    }
}
