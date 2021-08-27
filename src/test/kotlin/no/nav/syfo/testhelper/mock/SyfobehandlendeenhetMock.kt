package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient.Companion.PERSON_V2_ENHET_PATH
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader

val mockBehandlendeEnhetDTO = BehandlendeEnhetDTO(
    enhetId = ENHET_NR.value,
    navn = "Enheten",
)

class SyfobehandlendeenhetMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val behandlendeEnhetDTO = mockBehandlendeEnhetDTO

    val name = "behandlendeenhet"
    val server = mockServer(port)

    private fun mockServer(
        port: Int,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(PERSON_V2_ENHET_PATH) {
                    if (
                        getPersonIdentHeader() == ARBEIDSTAKER_FNR.value ||
                        getPersonIdentHeader() == ARBEIDSTAKER_ANNEN_FNR.value ||
                        getPersonIdentHeader() == ARBEIDSTAKER_NO_JOURNALFORING.value ||
                        getPersonIdentHeader() == ARBEIDSTAKER_IKKE_VARSEL.value
                    ) {
                        call.respond(behandlendeEnhetDTO)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "")
                    }
                }
            }
        }
    }
}
