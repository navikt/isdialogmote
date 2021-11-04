package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.AVLYSNING_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.ENDRING_TIDSTED_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.INNKALLING_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.REFERAT_PATH
import no.nav.syfo.testhelper.getRandomPort

class IsdialogmotepdfgenMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val pdfAvlysning = byteArrayOf(0x2E, 0x33)
    val pdfEndringTidSted = byteArrayOf(0x2E, 0x30)
    val pdfInnkalling = byteArrayOf(0x2E, 0x28)
    val pdfReferat = byteArrayOf(0x2E, 0x27)

    val name = "isdialogmotepdfgen"
    val server = mockIsdialogmotepdfgenServer(
        port
    )

    private fun mockIsdialogmotepdfgenServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(AVLYSNING_PATH) {
                    call.respond(pdfAvlysning)
                }
                post(ENDRING_TIDSTED_PATH) {
                    call.respond(pdfEndringTidSted)
                }
                post(ENDRING_TIDSTED_PATH) {
                    call.respond(pdfEndringTidSted)
                }
                post(INNKALLING_PATH) {
                    call.respond(pdfInnkalling)
                }
                post(INNKALLING_PATH) {
                    call.respond(pdfInnkalling)
                }
                post(REFERAT_PATH) {
                    call.respond(pdfReferat)
                }
            }
        }
    }
}
