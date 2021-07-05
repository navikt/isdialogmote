package no.nav.syfo.client.narmesteleder

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class NarmesteLederClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val modiasyforestClientId: String,
    modiasyforestBaseUrl: String
) {
    private val personNarmesteLederUrl: String
    private val personNarmesteLederV2Url: String

    init {
        this.personNarmesteLederUrl = "$modiasyforestBaseUrl$PERSON_NARMESTELEDER_PATH"
        this.personNarmesteLederV2Url = "$modiasyforestBaseUrl$PERSON_V2_NARMESTELEDER_PATH"
    }

    private val httpClient = httpClientDefault()

    suspend fun activeLeader(
        personIdentNumber: PersonIdentNumber,
        virksomhetsnummer: Virksomhetsnummer,
        token: String,
        callId: String,
        onBehalfOf: Boolean = false,
    ): NarmesteLederDTO? {
        return narmesteLedere(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
            onBehalfOf = onBehalfOf,
        ).filter {
            it.orgnummer == virksomhetsnummer.value
        }.maxByOrNull {
            it.fomDato
        }
    }

    suspend fun narmesteLedere(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
        onBehalfOf: Boolean,
    ): List<NarmesteLederDTO> {
        val url: String
        val oboToken: String?
        if (onBehalfOf) {
            oboToken = azureAdV2Client.getOnBehalfOfToken(
                scopeClientId = modiasyforestClientId,
                token = token
            )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")
            url = personNarmesteLederV2Url
        } else {
            oboToken = null
            url = personNarmesteLederUrl
        }

        return try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken ?: token))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_SUCCESS.inc()
            response.receive()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ): List<NarmesteLederDTO> {
        log.error(
            "Error while requesting NarmesteLedere of person from Modiasyforest with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_FAIL.inc()
        return emptyList()
    }

    companion object {
        const val PERSON_NARMESTELEDER_PATH = "/modiasyforest/api/internad/allnaermesteledere"
        const val PERSON_V2_NARMESTELEDER_PATH = "/modiasyforest/api/v2/internad/allnaermesteledere"
        private val log = LoggerFactory.getLogger(NarmesteLederClient::class.java)
    }
}
