package no.nav.syfo.testhelper.mock

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.INNKALLING_ARBEIDSTAKER_PATH
import no.nav.syfo.testhelper.getRandomPort

class IsdialogmotepdfgenMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val pdfInnkallingArbeidstaker = byteArrayOf(0x2E, 0x31)

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
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            routing {
                post(INNKALLING_ARBEIDSTAKER_PATH) {
                    call.respond(pdfInnkallingArbeidstaker)
                }
            }
        }
    }
}
