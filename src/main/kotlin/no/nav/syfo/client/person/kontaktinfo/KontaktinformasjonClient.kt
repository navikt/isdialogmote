package no.nav.syfo.client.person.kontaktinfo

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class KontaktinformasjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val cache: RedisStore,
    private val isproxyClientId: String,
    isproxyBaseUrl: String
) {
    private val personKontaktinfoUrl: String = "$isproxyBaseUrl$ISPROXY_DKIF_KONTAKTINFORMASJON_PATH"

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
            scopeClientId = isproxyClientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Isproxy: Failed to get OBO token")
        val cacheKey = "${CACHE_KONTAKTINFORMASJON_KEY_PREFIX}${personIdentNumber.value}"
        val cachedKontaktinformasjon = cache.getObject<DigitalKontaktinfoBolk>(cacheKey)
        return when (cachedKontaktinformasjon) {
            null ->
                try {
                    val response: HttpResponse = httpClient.get(personKontaktinfoUrl) {
                        header(HttpHeaders.Authorization, bearerHeader(oboToken))
                        header(NAV_CALL_ID_HEADER, callId)
                        header(NAV_PERSONIDENTER_HEADER, personIdentNumber.value)
                        accept(ContentType.Application.Json)
                    }
                    val digitalKontaktinfoBolkResponse = response.receive<DigitalKontaktinfoBolk>()
                    COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS.increment()
                    cache.setObject(cacheKey, digitalKontaktinfoBolkResponse, CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS)
                    digitalKontaktinfoBolkResponse
                } catch (responseException: ResponseException) {
                    log.error(
                        "Error while requesting Kontaktinformasjon of person from Isproxy with {}, {}",
                        StructuredArguments.keyValue("statusCode", responseException.response.status.value.toString()),
                        callIdArgument(callId)
                    )
                    COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL.increment()
                    null
                }
            else -> cachedKontaktinformasjon
        }
    }

    companion object {
        private const val ISPROXY_DKIF_PATH = "/api/v1/dkif"
        const val ISPROXY_DKIF_KONTAKTINFORMASJON_PATH = "$ISPROXY_DKIF_PATH/kontaktinformasjon"

        const val CACHE_KONTAKTINFORMASJON_KEY_PREFIX = "person-kontaktinformasjon-"
        const val CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS = 600L

        private val log = LoggerFactory.getLogger(KontaktinformasjonClient::class.java)
    }
}
