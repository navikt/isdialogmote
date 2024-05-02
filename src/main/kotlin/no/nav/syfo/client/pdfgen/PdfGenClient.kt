package no.nav.syfo.client.pdfgen

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.toReadableString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class PdfGenClient(
    private val pdfGenBaseUrl: String
) {

    private val httpClient = httpClientDefault()

    suspend fun pdfAvlysning(
        callId: String,
        mottakerNavn: String? = null,
        mottakerFodselsnummer: String? = null,
        pdfContent: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            mottakerNavn = mottakerNavn,
            mottakerFodselsnummer = mottakerFodselsnummer,
            pdfContent = pdfContent,
            pdfUrl = "$pdfGenBaseUrl$AVLYSNING_PATH",
        )
    }

    suspend fun pdfEndringTidSted(
        callId: String,
        mottakerNavn: String? = null,
        mottakerFodselsnummer: String? = null,
        pdfContent: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            mottakerNavn = mottakerNavn,
            mottakerFodselsnummer = mottakerFodselsnummer,
            pdfContent = pdfContent,
            pdfUrl = "$pdfGenBaseUrl$ENDRING_TIDSTED_PATH",
        )
    }

    suspend fun pdfInnkalling(
        callId: String,
        mottakerNavn: String? = null,
        mottakerFodselsnummer: String? = null,
        pdfContent: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            mottakerNavn = mottakerNavn,
            mottakerFodselsnummer = mottakerFodselsnummer,
            pdfContent = pdfContent,
            pdfUrl = "$pdfGenBaseUrl$INNKALLING_PATH",
        )
    }

    suspend fun pdfReferat(
        callId: String,
        pdfContent: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            mottakerNavn = null,
            mottakerFodselsnummer = null,
            pdfContent = pdfContent,
            pdfUrl = "$pdfGenBaseUrl$REFERAT_PATH",
        )
    }

    private suspend fun getPdf(
        callId: String,
        mottakerNavn: String?,
        mottakerFodselsnummer: String?,
        pdfContent: List<DocumentComponentDTO>,
        pdfUrl: String,
    ): ByteArray? {
        return try {
            val requestBody =
                DialogmoteHendelsePdfContent(
                    mottakerNavn = mottakerNavn,
                    mottakerFodselsnummer = mottakerFodselsnummer,
                    datoSendt = LocalDateTime.now().toReadableString(),
                    documentComponents = pdfContent.sanitizeForPdfGen(),
                )
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            COUNT_CALL_PDFGEN_SUCCESS.increment()
            response.body()
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
            "Error while requesting PDF from ispdfgen with {}, {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("url", url),
            callIdArgument(callId)
        )
        COUNT_CALL_PDFGEN_FAIL.increment()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isdialogmote"
        const val AVLYSNING_PATH = "$API_BASE_PATH/avlysning-v2"
        const val ENDRING_TIDSTED_PATH = "$API_BASE_PATH/endring-tid-sted-v2"
        const val INNKALLING_PATH = "$API_BASE_PATH/innkalling-v2"
        const val REFERAT_PATH = "$API_BASE_PATH/referat-v2"

        val log: Logger = LoggerFactory.getLogger(PdfGenClient::class.java)
        val illegalCharacters = listOf('\u0002')
    }
}

fun List<DocumentComponentDTO>.sanitizeForPdfGen(): List<DocumentComponentDTO> = this.map {
    it.copy(
        texts = it.texts.map { text ->
            text.toCharArray().filter { char ->
                if (char in PdfGenClient.illegalCharacters) {
                    PdfGenClient.log.warn("Illegal character in document: %x".format(char.code))
                    false
                } else {
                    true
                }
            }.joinToString("")
        }
    )
}
