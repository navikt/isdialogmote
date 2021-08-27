package no.nav.syfo.client.person.kontaktinfo

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class KontaktinformasjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val cache: RedisStore,
    private val syfopersonClientId: String,
    syfopersonBaseUrl: String
) {
    private val personKontaktinfoV2Url: String = "$syfopersonBaseUrl$PERSON_V2_KONTAKTINFORMASJON_PATH"

    private val httpClient = httpClientDefault()

    suspend fun isDigitalVarselEnabled(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val kontaktinfo = this.kontaktinformasjon(personIdentNumber, token, callId)
        return kontaktinfo?.kontaktinfo?.isDigitalVarselEnabled(personIdentNumber) ?: false
    }

    private suspend fun kontaktinformasjon(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): DigitalKontaktinfoBolk? {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfopersonClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")
        val cacheKey = "${CACHE_KONTAKTINFORMASJON_KEY_PREFIX}${personIdentNumber.value}"
        val cachedKontaktinformasjon = cache.getObject<DigitalKontaktinfoBolk>(cacheKey)
        return when (cachedKontaktinformasjon) {
            null ->
                try {
                    val response: HttpResponse = httpClient.get(personKontaktinfoV2Url) {
                        header(HttpHeaders.Authorization, bearerHeader(oboToken))
                        header(NAV_CALL_ID_HEADER, callId)
                        header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                        accept(ContentType.Application.Json)
                    }
                    val digitalKontaktinfoBolkResponse = response.receive<DigitalKontaktinfoBolk>()
                    COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS.increment()
                    cache.setObject(cacheKey, digitalKontaktinfoBolkResponse, CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS)
                    digitalKontaktinfoBolkResponse
                } catch (e: ClientRequestException) {
                    handleUnexpectedResponseException(e.response, callId)
                    null
                } catch (e: ServerResponseException) {
                    handleUnexpectedResponseException(e.response, callId)
                    null
                }
            else -> cachedKontaktinformasjon
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting Kontaktinformasjon of person from Syfoperson with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL.increment()
    }

    companion object {
        const val PERSON_V2_PATH = "/syfoperson/api/v2/person"
        const val PERSON_V2_KONTAKTINFORMASJON_PATH = "$PERSON_V2_PATH/kontaktinformasjon"

        const val CACHE_KONTAKTINFORMASJON_KEY_PREFIX = "person-kontaktinformasjon-"
        const val CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS = 600L

        private val log = LoggerFactory.getLogger(KontaktinformasjonClient::class.java)
    }
}
