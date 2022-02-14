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
    private val avlysningUrl: String
    private val endringTidStedUrl: String
    private val innkallingUrl: String
    private val referatUrl: String

    init {
        this.avlysningUrl = "$pdfGenBaseUrl$AVLYSNING_PATH"
        this.endringTidStedUrl = "$pdfGenBaseUrl$ENDRING_TIDSTED_PATH"
        this.innkallingUrl = "$pdfGenBaseUrl$INNKALLING_PATH"
        this.referatUrl = "$pdfGenBaseUrl$REFERAT_PATH"
    }

    private val httpClient = httpClientDefault()

    suspend fun pdfAvlysning(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        deltager: String
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = "$avlysningUrl-$deltager",
        )
    }

    suspend fun pdfEndringTidSted(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        deltager: String
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = "$endringTidStedUrl-$deltager",
        )
    }

    suspend fun pdfInnkalling(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        deltager: String
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = "$innkallingUrl-$deltager",
        )
    }

    suspend fun pdfReferat(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        deltager: String? = null
    ): ByteArray? {
        val url = if (deltager != null) {
            "$referatUrl-$deltager"
        } else {
            referatUrl
        }

        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = url,
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
            COUNT_CALL_PDFGEN_SUCCESS.increment()
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
        COUNT_CALL_PDFGEN_FAIL.increment()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isdialogmote"
        const val AVLYSNING_PATH = "$API_BASE_PATH/avlysning"
        const val ENDRING_TIDSTED_PATH = "$API_BASE_PATH/endring-tidsted"
        const val INNKALLING_PATH = "$API_BASE_PATH/innkalling"
        const val REFERAT_PATH = "$API_BASE_PATH/referat"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}
