package no.nav.syfo.testhelper.mock

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient.Companion.DISTRIBUER_JOURNALPOST_PATH
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonRequest
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonResponse
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.getRandomPort
import java.util.UUID

class DokdistFordelingMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "dokdist"
    val server = mockDokdistFordelingServer(port)

    private fun mockDokdistFordelingServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(DISTRIBUER_JOURNALPOST_PATH) {
                    val journalpostdistribusjonRequest = call.receive<JournalpostdistribusjonRequest>()
                    if (journalpostdistribusjonRequest.journalpostId == UserConstants.JOURNALPOST_ID_MOTTAKER_GONE.toString()) {
                        call.respond(HttpStatusCode.Gone, "")
                    } else {
                        call.respond(JournalpostdistribusjonResponse(bestillingsId = UUID.randomUUID().toString()))
                    }
                }
            }
        }
    }
}
