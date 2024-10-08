package no.nav.syfo.testhelper.mock

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.ereg.EregClient.Companion.EREG_PATH
import no.nav.syfo.client.ereg.EregOrganisasjonNavn
import no.nav.syfo.client.ereg.EregOrganisasjonResponse
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_EREG_FAILS
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort

class EregMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "ereg"

    val server = embeddedServer(
        factory = Netty,
        port = port,
    ) {
        installContentNegotiation()
        routing {
            get("$EREG_PATH/${VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value}") {
                call.respond(EregOrganisasjonResponse(EregOrganisasjonNavn("Butikken", "")))
            }
            get("$EREG_PATH/${VIRKSOMHETSNUMMER_EREG_FAILS.value}") {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
