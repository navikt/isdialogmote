package no.nav.syfo.client.person.kontaktinfo

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class KontaktinformasjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val cache: RedisStore,
    private val clientId: String,
    baseUrl: String
) {
    private val personKontaktinfoUrl: String = "$baseUrl$KRR_KONTAKTINFORMASJON_BOLK_PATH"

    private val httpClient = httpClientDefault()

    suspend fun isDigitalVarselEnabled(
        personIdent: PersonIdent,
        token: String,
        callId: String,
    ): Boolean {
        val kontaktinfo = this.kontaktinformasjon(personIdent, token, callId)
        return kontaktinfo?.personer?.isDigitalVarselEnabled(personIdent) ?: false
    }

    private suspend fun kontaktinformasjon(
        personIdent: PersonIdent,
        token: String,
        callId: String,
    ): DigitalKontaktinfoBolk? {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token
        )?.accessToken ?: throw RuntimeException("Failed to request access to Digdir-krr-proxy: Failed to get OBO token")
        val cacheKey = "${CACHE_KONTAKTINFORMASJON_KEY_PREFIX}${personIdent.value}"
        val cachedKontaktinformasjon = cache.getObject<DigitalKontaktinfoBolk>(cacheKey)
        return when (cachedKontaktinformasjon) {
            null ->
                try {
                    val request = DigitalKontaktinfoBolkRequestBody(
                        personidenter = listOf(personIdent.value),
                    )
                    val response: HttpResponse = httpClient.post(personKontaktinfoUrl) {
                        header(HttpHeaders.Authorization, bearerHeader(oboToken))
                        header(NAV_CALL_ID_HEADER, callId)
                        accept(ContentType.Application.Json)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                    val digitalKontaktinfoBolkResponse = response.body<DigitalKontaktinfoBolk>()
                    COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS.increment()
                    cache.setObject(cacheKey, digitalKontaktinfoBolkResponse, CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS)
                    digitalKontaktinfoBolkResponse
                } catch (responseException: ResponseException) {
                    log.error(
                        "Error while requesting Kontaktinformasjon of person from Digdir-krr-proxy with {}, {}",
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
        const val KRR_KONTAKTINFORMASJON_BOLK_PATH = "/rest/v1/personer"

        const val CACHE_KONTAKTINFORMASJON_KEY_PREFIX = "person-kontaktinformasjon-"
        const val CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS = 600L

        private val log = LoggerFactory.getLogger(KontaktinformasjonClient::class.java)
    }
}
