package no.nav.syfo.client.person.adressebeskyttelse

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.person.COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class AdressebeskyttelseClient(
    private val cache: RedisStore,
    syfopersonBaseUrl: String,
) {
    private val personAdressebeskyttelseUrl: String = "$syfopersonBaseUrl$PERSON_ADRESSEBESKYTTELSE_PATH"

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun hasAdressebeskyttelse(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String
    ): Boolean {
        val cacheKey = "$CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX${personIdentNumber.value}"
        when (val cachedAdresseBeskyttelse = cache.get(cacheKey)) {
            null ->
                return try {
                    val response: HttpResponse = httpClient.get(personAdressebeskyttelseUrl) {
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
                }
            else -> return cachedAdresseBeskyttelse.toBoolean()
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

    companion object {
        const val PERSON_PATH = "/syfoperson/api/person"
        const val PERSON_ADRESSEBESKYTTELSE_PATH = "$PERSON_PATH/adressebeskyttelse"

        const val CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX = "person-adressebeskyttelse-"
        const val CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS = 3600

        private val log = LoggerFactory.getLogger(AdressebeskyttelseClient::class.java)
    }
}
