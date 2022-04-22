package no.nav.syfo.testhelper.mock

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient.Companion.DISTRIBUER_JOURNALPOST_PATH
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonResponse
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
                    call.respond(JournalpostdistribusjonResponse(bestillingsId = UUID.randomUUID().toString()))
                }
            }
        }
    }
}
