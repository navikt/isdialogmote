package no.nav.syfo.testhelper.mock

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient.Companion.PERSON_ENHET_PATH
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
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

    val server = mockServer(port)

    private fun mockServer(
        port: Int,
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
                get(PERSON_ENHET_PATH) {
                    if (getPersonIdentHeader() == ARBEIDSTAKER_FNR.value) {
                        call.respond(behandlendeEnhetDTO)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "")
                    }
                }
            }
        }
    }
}
