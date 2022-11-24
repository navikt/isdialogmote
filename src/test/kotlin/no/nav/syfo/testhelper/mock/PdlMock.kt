package no.nav.syfo.testhelper.mock

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.pdl.Gradering
import no.nav.syfo.client.pdl.PdlRequest
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.generator.generatePdlIdenter
import no.nav.syfo.testhelper.generator.generatePdlPersonResponse
import no.nav.syfo.testhelper.getRandomPort

class PdlMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "pdl"
    val server = embeddedServer(
        factory = Netty,
        port = port
    ) {
        installContentNegotiation()
        routing {
            post {
                val pdlRequest = call.receive<PdlRequest>()
                val isHentIdenter = pdlRequest.query.contains("hentIdenter")
                if (isHentIdenter) {
                    if (pdlRequest.variables.ident == UserConstants.ARBEIDSTAKER_TREDJE_FNR.value) {
                        call.respond(generatePdlIdenter("enAnnenIdent"))
                    } else {
                        call.respond(generatePdlIdenter(pdlRequest.variables.ident))
                    }
                } else {
                    if (pdlRequest.variables.ident == ARBEIDSTAKER_ADRESSEBESKYTTET.value) {
                        call.respond(generatePdlPersonResponse(Gradering.STRENGT_FORTROLIG))
                    } else {
                        call.respond(generatePdlPersonResponse())
                    }
                }
            }
        }
    }
}
