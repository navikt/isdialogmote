package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.pdl.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.generator.generatePdlPersonResponse
import no.nav.syfo.testhelper.getRandomPort

class PdlMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "pdl"
    val server = mockPdlServer(port)

    private fun mockPdlServer(
        port: Int,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post {
                    val pdlRequest = call.receive<PdlRequest>()
                    if (ARBEIDSTAKER_ADRESSEBESKYTTET.value == pdlRequest.variables.ident) {
                        call.respond(generatePdlPersonResponse(Gradering.STRENGT_FORTROLIG))
                    } else {
                        call.respond(generatePdlPersonResponse())
                    }
                }
            }
        }
    }
}
