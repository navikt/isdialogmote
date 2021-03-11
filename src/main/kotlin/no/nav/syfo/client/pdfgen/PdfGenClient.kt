package no.nav.syfo.client.pdfgen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import no.nav.syfo.client.pdfgen.model.*
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class PdfGenClient(
    pdfGenBaseUrl: String
) {
    private val avlysningArbeidstakerUrl: String
    private val endringTidStedArbeidstakerUrl: String
    private val inkallingArbeidstakerUrl: String

    init {
        this.avlysningArbeidstakerUrl = "$pdfGenBaseUrl$AVLYSNING_ARBEIDSTAKER_PATH"
        this.endringTidStedArbeidstakerUrl = "$pdfGenBaseUrl$ENDRING_TIDSTED_ARBEIDSTAKER_PATH"
        this.inkallingArbeidstakerUrl = "$pdfGenBaseUrl$INNKALLING_ARBEIDSTAKER_PATH"
    }

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
    }

    suspend fun pdfAvlysningArbeidstaker(
        callId: String,
        pdfBody: PdfModelAvlysningArbeidstaker,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            pdfBody = pdfBody,
            pdfUrl = avlysningArbeidstakerUrl,
        )
    }

    suspend fun pdfEndringTidStedArbeidstaker(
        callId: String,
        pdfBody: PdfModelEndringTidStedArbeidstaker,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            pdfBody = pdfBody,
            pdfUrl = endringTidStedArbeidstakerUrl,
        )
    }

    suspend fun pdfInnkallingArbeidstaker(
        callId: String,
        pdfBody: PdfModelInnkallingArbeidstaker,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            pdfBody = pdfBody,
            pdfUrl = inkallingArbeidstakerUrl
        )
    }

    private suspend fun getPdf(
        callId: String,
        pdfBody: Any,
        pdfUrl: String,
    ): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = pdfBody
            }
            COUNT_CALL_PDFGEN_SUCCESS.inc()
            response.receive()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        }
    }

    private fun handleUnexpectedResponseException(
        url: String,
        response: HttpResponse,
        callId: String,
    ): ByteArray? {
        log.error(
            "Error while requesting PDF from Isdialogmotepdfgen with {}, {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("url", url),
            callIdArgument(callId)
        )
        COUNT_CALL_PDFGEN_FAIL.inc()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isdialogmote"
        const val AVLYSNING_ARBEIDSTAKER_PATH = "$API_BASE_PATH/avlysning-arbeidstaker"
        const val ENDRING_TIDSTED_ARBEIDSTAKER_PATH = "$API_BASE_PATH/endring-tidsted-arbeidstaker"
        const val INNKALLING_ARBEIDSTAKER_PATH = "$API_BASE_PATH/innkalling-arbeidstaker"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}
