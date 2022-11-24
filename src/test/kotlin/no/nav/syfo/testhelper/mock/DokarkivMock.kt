package no.nav.syfo.testhelper.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.dokarkiv.DokarkivClient.Companion.JOURNALPOST_PATH
import no.nav.syfo.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.getRandomPort

class DokarkivMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val journalpostResponse = JournalpostResponse(
        journalpostId = 12345678,
        journalstatus = "journalstatus",
    )

    val name = "dokarkiv"
    val server = mockDokarkivServer(
        port
    )

    private fun mockDokarkivServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(JOURNALPOST_PATH) {
                    val journalpostRequest = call.receive<JournalpostRequest>()
                    if (journalpostRequest.bruker?.id == ARBEIDSTAKER_NO_JOURNALFORING.value) {
                        call.respond(HttpStatusCode.InternalServerError, "")
                    } else if (journalpostRequest.sak.sakstype.trim() == "") {
                        call.respond(HttpStatusCode.BadRequest, "")
                    } else {
                        call.respond(journalpostResponse)
                    }
                }
            }
        }
    }
}
