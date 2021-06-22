package no.nav.syfo.client.person.adressebeskyttelse

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class AdressebeskyttelseClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfopersonClientId: String,
    private val cache: RedisStore,
    syfopersonBaseUrl: String,
) {
    private val personAdressebeskyttelseUrl: String = "$syfopersonBaseUrl$PERSON_ADRESSEBESKYTTELSE_PATH"
    private val personV2AdressebeskyttelseUrl: String = "$syfopersonBaseUrl$PERSON_V2_ADRESSEBESKYTTELSE_PATH"
    private val httpClient = httpClientDefault()

    suspend fun hasAdressebeskyttelseWithOBO(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfopersonClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Enhet: Failed to get OBO token")

        return hasAdressebeskyttelse(
            personIdentNumber = personIdentNumber,
            token = oboToken,
            url = personV2AdressebeskyttelseUrl,
            callId = callId,
        )
    }

    suspend fun hasAdressebeskyttelse(
        personIdentNumber: PersonIdentNumber,
        token: String,
        url: String = personAdressebeskyttelseUrl,
        callId: String,
    ): Boolean {
        val cacheKey = "$CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX${personIdentNumber.value}"
        val cachedAdressebeskyttelse = cache.get(cacheKey)
        return when (cachedAdressebeskyttelse) {
            null -> {
                val timer = HISTOGRAM_CALL_PERSON_ADRESSEBESKYTTELSE_TIMER.startTimer()
                try {
                    val response: HttpResponse = httpClient.get(url) {
                        header(HttpHeaders.Authorization, bearerHeader(token))
                        header(NAV_CALL_ID_HEADER, callId)
                        header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                        accept(ContentType.Application.Json)
                    }
                    val adressebeskyttelseResponse = response.receive<AdressebeskyttelseResponse>()
                    COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS.inc()
                    cache.set(
                        cacheKey,
                        adressebeskyttelseResponse.beskyttet.toString(),
                        CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS
                    )
                    adressebeskyttelseResponse.beskyttet
                } catch (e: ClientRequestException) {
                    handleUnexpectedResponseException(e.response, callId)
                } catch (e: ServerResponseException) {
                    handleUnexpectedResponseException(e.response, callId)
                } catch (e: ClosedReceiveChannelException) {
                    handleClosedReceiveChannelException(e)
                } finally {
                    timer.observeDuration()
                }
            }
            else -> cachedAdressebeskyttelse.toBoolean()
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ): Boolean {
        log.error(
            "Error while requesting Adressebeskyttelse of person from Syfoperson with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_FAIL.inc()
        return true
    }

    private fun handleClosedReceiveChannelException(
        e: ClosedReceiveChannelException
    ): Boolean {
        COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_FAIL.inc()
        throw RuntimeException("Caught ClosedReceiveChannelException in hasAdressebeskyttelse", e)
    }

    companion object {
        const val PERSON_PATH = "/syfoperson/api/person"
        const val PERSON_ADRESSEBESKYTTELSE_PATH = "$PERSON_PATH/adressebeskyttelse"

        const val PERSON_V2_PATH = "/syfoperson/v2/api/person"
        const val PERSON_V2_ADRESSEBESKYTTELSE_PATH = "$PERSON_V2_PATH/adressebeskyttelse"

        const val CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX = "person-adressebeskyttelse-"
        const val CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS = 3600

        private val log = LoggerFactory.getLogger(AdressebeskyttelseClient::class.java)
    }
}
