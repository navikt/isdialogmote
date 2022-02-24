package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.ereg.EregClient.Companion.EREG_PATH
import no.nav.syfo.client.ereg.EregOrganisasjonNavn
import no.nav.syfo.client.ereg.EregOrganisasjonResponse
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort

class IsproxyMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "isproxy"

    val server = embeddedServer(
        factory = Netty,
        port = port,
    ) {
        installContentNegotiation()
        routing {
            get("$EREG_PATH/${VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value}") {
                call.respond(EregOrganisasjonResponse(EregOrganisasjonNavn("Butikken", "")))
            }
        }
    }
}
