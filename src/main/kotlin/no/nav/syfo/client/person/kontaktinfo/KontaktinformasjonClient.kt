package no.nav.syfo.client.person.kontaktinfo

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class KontaktinformasjonClient(
    private val cache: RedisStore,
    syfopersonBaseUrl: String
) {
    private val personKontaktinfoUrl: String = "$syfopersonBaseUrl$PERSON_KONTAKTINFORMASJON_PATH"

    private val httpClient = httpClientDefault()

    suspend fun kontaktinformasjon(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String
    ): DigitalKontaktinfoBolk? {
        val cacheKey = "${CACHE_KONTAKTINFORMASJON_KEY_PREFIX}${personIdentNumber.value}"
        val cachedKontaktinformasjon = cache.getObject<DigitalKontaktinfoBolk>(cacheKey)
        return when (cachedKontaktinformasjon) {
            null ->
                try {
                    val response: HttpResponse = httpClient.get(personKontaktinfoUrl) {
                        header(HttpHeaders.Authorization, bearerHeader(token))
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
        const val PERSON_PATH = "/syfoperson/api/person"
        const val PERSON_KONTAKTINFORMASJON_PATH = "$PERSON_PATH/kontaktinformasjon"

        const val CACHE_KONTAKTINFORMASJON_KEY_PREFIX = "person-kontaktinformasjon-"
        const val CACHE_KONTAKTINFORMASJON_EXPIRE_SECONDS = 600

        private val log = LoggerFactory.getLogger(KontaktinformasjonClient::class.java)
    }
}
