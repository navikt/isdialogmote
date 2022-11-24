package no.nav.syfo.testhelper.mock

import io.ktor.http.ContentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.AVLYSNING_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.ENDRING_TIDSTED_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.INNKALLING_PATH
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.REFERAT_PATH
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.configure

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
            install(ContentNegotiation) {
                jackson(ContentType.Any) {
                    configure()
                }
            }
            routing {
                post(AVLYSNING_PATH) {
                    call.respond(pdfAvlysning)
                }
                post(ENDRING_TIDSTED_PATH) {
                    call.respond(pdfEndringTidSted)
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
