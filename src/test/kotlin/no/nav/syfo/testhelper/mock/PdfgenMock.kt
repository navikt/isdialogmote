package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.AVLYSNING_PATH
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.ENDRING_TIDSTED_PATH
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.INNKALLING_PATH
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.REFERAT_PATH

val pdfAvlysning = byteArrayOf(0x2E, 0x33)
val pdfEndringTidSted = byteArrayOf(0x2E, 0x30)
val pdfInnkalling = byteArrayOf(0x2E, 0x28)
val pdfReferat = byteArrayOf(0x2E, 0x27)

fun MockRequestHandleScope.pdfGenMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(AVLYSNING_PATH) -> {
            respond(content = pdfAvlysning)
        }
        requestUrl.endsWith(ENDRING_TIDSTED_PATH) -> {
            respond(content = pdfEndringTidSted)
        }
        requestUrl.endsWith(INNKALLING_PATH) -> {
            respond(content = pdfInnkalling)
        }
        requestUrl.endsWith(REFERAT_PATH) -> {
            respond(content = pdfReferat)
        }

        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
