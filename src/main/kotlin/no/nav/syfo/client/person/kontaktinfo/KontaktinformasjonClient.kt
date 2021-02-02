package no.nav.syfo.client.person.kontaktinfo

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
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL
import no.nav.syfo.client.person.COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class KontaktinformasjonClient(
    syfopersonBaseUrl: String
) {
    private val personKontaktinfoUrl: String

    init {
        this.personKontaktinfoUrl = "$syfopersonBaseUrl$PERSON_KONTAKTINFORMASJON_PATH"
    }

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun kontaktinformasjon(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String?
    ): DigitalKontaktinfoBolk? {
        return try {
            val response: HttpResponse = httpClient.get(personKontaktinfoUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS.inc()
            response.receive<DigitalKontaktinfoBolk>()
        } catch (e: ClientRequestException) {
            handleUnexpectedReponseException(e.response)
            null
        } catch (e: ServerResponseException) {
            handleUnexpectedReponseException(e.response)
            null
        }
    }

    private fun handleUnexpectedReponseException(response: HttpResponse) {
        log.error(
            "Error while requesting Kontaktinformasjon of person from Syfoperson with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString())
        )
        COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL.inc()
    }

    companion object {
        const val PERSON_PATH = "/syfoperson/api/person"
        const val PERSON_KONTAKTINFORMASJON_PATH = "$PERSON_PATH/kontaktinformasjon"
        private val log = LoggerFactory.getLogger(KontaktinformasjonClient::class.java)
    }
}
