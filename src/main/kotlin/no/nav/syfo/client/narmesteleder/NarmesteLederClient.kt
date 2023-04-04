package no.nav.syfo.client.narmesteleder

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class NarmesteLederClient(
    narmesteLederBaseUrl: String,
    private val narmestelederClientId: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val tokendingsClient: TokendingsClient,
    private val cache: RedisStore,
) {
    private val narmesteLederPath = "$narmesteLederBaseUrl$CURRENT_NARMESTELEDER_PATH"
    private val ansatteNarmesteLederSelvbetjeningPath = "$narmesteLederBaseUrl$NARMESTELEDERE_SELVBETJENING_PATH"

    private val httpClient = httpClientDefault()

    suspend fun activeLeder(
        personIdent: PersonIdent,
        virksomhetsnummer: Virksomhetsnummer,
        callId: String,
        token: String,
    ): NarmesteLederRelasjonDTO? {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = narmestelederClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request active leader: Failed to get OBO token")

        return try {
            val narmesteLederRelasjon =
                httpClient.get(narmesteLederPath) {
                    header(HttpHeaders.Authorization, bearerHeader(oboToken))
                    header(NAV_PERSONIDENT_HEADER, personIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                }.body<List<NarmesteLederRelasjonDTO>>()
            COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS.increment()
            narmesteLederRelasjon.filter { it.virksomhetsnummer == virksomhetsnummer.value }
                .firstOrNull { it.status == NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name }
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, callId)
            null
        }
    }

    suspend fun getAktiveAnsatte(
        narmesteLederIdent: PersonIdent,
        tokenx: String,
        callId: String,
    ): List<NarmesteLederRelasjonDTO> {
        val cacheKey = "$CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX${narmesteLederIdent.value}"
        val cachedAktiveAnsatte = cache.getListObject<NarmesteLederRelasjonDTO>(cacheKey)
        return if (cachedAktiveAnsatte != null) {
            COUNT_CALL_NARMESTE_LEDER_CACHE_HIT.increment()
            cachedAktiveAnsatte
        } else {
            val token = tokendingsClient.getOnBehalfOfToken(
                scopeClientId = narmestelederClientId,
                token = tokenx,
            ).accessToken

            try {
                val ansatte = httpClient.get(ansatteNarmesteLederSelvbetjeningPath) {
                    header(HttpHeaders.Authorization, bearerHeader(token))
                    header(NAV_PERSONIDENT_HEADER, narmesteLederIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                }.body<List<NarmesteLederRelasjonDTO>>()
                COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS.increment()
                COUNT_CALL_NARMESTE_LEDER_CACHE_MISS.increment()
                val aktiveAnsatte = ansatte.filter {
                    it.narmesteLederPersonIdentNumber == narmesteLederIdent.value &&
                        it.status == NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name
                }
                cache.setObject(
                    cacheKey,
                    aktiveAnsatte,
                    CACHE_NARMESTE_LEDER_EXPIRE_SECONDS,
                )
                aktiveAnsatte
            } catch (e: ClientRequestException) {
                handleUnexpectedResponseException(e.response, callId)
                emptyList()
            } catch (e: ServerResponseException) {
                handleUnexpectedResponseException(e.response, callId)
                emptyList()
            }
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
        const val CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX = "narmeste-leder-aktive-ansatte-"
        const val CACHE_NARMESTE_LEDER_EXPIRE_SECONDS = 3600L

        const val CURRENT_NARMESTELEDER_PATH = "/api/v1/narmestelederrelasjon/personident"
        const val NARMESTELEDERE_SELVBETJENING_PATH = "/api/selvbetjening/v1/narmestelederrelasjoner"
    }
}
