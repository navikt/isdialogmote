package no.nav.syfo.client.pdfgen

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class PdfGenClient(
    pdfGenBaseUrl: String
) {
    private val avlysningArbeidstakerUrl: String
    private val avlysningArbeidsgiverUrl: String
    private val endringTidStedArbeidstakerUrl: String
    private val endringTidStedArbeidsgiverUrl: String
    private val innkallingArbeidstakerUrl: String
    private val innkallingArbeidsgiverUrl: String

    init {
        this.avlysningArbeidstakerUrl = "$pdfGenBaseUrl$AVLYSNING_ARBEIDSTAKER_PATH"
        this.avlysningArbeidsgiverUrl = "$pdfGenBaseUrl$AVLYSNING_ARBEIDSGIVER_PATH"
        this.endringTidStedArbeidstakerUrl = "$pdfGenBaseUrl$ENDRING_TIDSTED_ARBEIDSTAKER_PATH"
        this.endringTidStedArbeidsgiverUrl = "$pdfGenBaseUrl$ENDRING_TIDSTED_ARBEIDSGIVER_PATH"
        this.innkallingArbeidstakerUrl = "$pdfGenBaseUrl$INNKALLING_ARBEIDSTAKER_PATH"
        this.innkallingArbeidsgiverUrl = "$pdfGenBaseUrl$INNKALLING_ARBEIDSGIVER_PATH"
    }

    private val httpClient = httpClientDefault()

    suspend fun pdfAvlysningArbeidstaker(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = avlysningArbeidstakerUrl,
        )
    }

    suspend fun pdfAvlysningArbeidsgiver(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = avlysningArbeidsgiverUrl,
        )
    }

    suspend fun pdfEndringTidStedArbeidstaker(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = endringTidStedArbeidstakerUrl,
        )
    }

    suspend fun pdfEndringTidStedArbeidsgiver(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = endringTidStedArbeidsgiverUrl,
        )
    }

    suspend fun pdfInnkallingArbeidstaker(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = innkallingArbeidstakerUrl,
        )
    }

    suspend fun pdfInnkallingArbeidsgiver(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = innkallingArbeidsgiverUrl,
        )
    }

    private suspend fun getPdf(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        pdfUrl: String,
    ): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = documentComponentDTOList
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
        const val AVLYSNING_ARBEIDSGIVER_PATH = "$API_BASE_PATH/avlysning-arbeidsgiver"
        const val ENDRING_TIDSTED_ARBEIDSTAKER_PATH = "$API_BASE_PATH/endring-tidsted-arbeidstaker"
        const val ENDRING_TIDSTED_ARBEIDSGIVER_PATH = "$API_BASE_PATH/endring-tidsted-arbeidsgiver"
        const val INNKALLING_ARBEIDSTAKER_PATH = "$API_BASE_PATH/innkalling-arbeidstaker"
        const val INNKALLING_ARBEIDSGIVER_PATH = "$API_BASE_PATH/innkalling-arbeidsgiver"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}
