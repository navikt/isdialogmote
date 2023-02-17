package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.pdl.Gradering
import no.nav.syfo.client.pdl.PdlRequest
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.generator.generatePdlError
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
                    when (pdlRequest.variables.ident) {
                        UserConstants.ARBEIDSTAKER_TREDJE_FNR.value -> {
                            call.respond(
                                generatePdlIdenter(
                                    UserConstants.ARBEIDSTAKER_TREDJE_FNR.value,
                                    UserConstants.ARBEIDSTAKER_FJERDE_FNR.value,
                                )
                            )
                        }
                        UserConstants.ARBEIDSTAKER_IKKE_AKTIVT_FNR.value -> {
                            call.respond(generatePdlIdenter("dummyIdent"))
                        }
                        UserConstants.ARBEIDSTAKER_WITH_ERROR_FNR.value -> {
                            call.respond(
                                generatePdlIdenter(pdlRequest.variables.ident)
                                    .copy(errors = generatePdlError(code = "not_found"))
                            )
                        }
                        else -> {
                            call.respond(generatePdlIdenter(pdlRequest.variables.ident))
                        }
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
