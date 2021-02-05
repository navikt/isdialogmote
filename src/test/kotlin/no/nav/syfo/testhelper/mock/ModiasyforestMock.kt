package no.nav.syfo.testhelper.mock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient.Companion.PERSON_NARMESTELEDER_PATH
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_NO_PLANLAGTMOTE
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader
import java.time.LocalDate

val narmesteLederDTOVirksomhetHasLeader = NarmesteLederDTO(
    navn = "",
    aktoerId = "",
    fomDato = LocalDate.now().minusDays(10),
    orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    organisasjonsnavn = "",
)

val narmesteLederDTOVirksomhetOther = narmesteLederDTOVirksomhetHasLeader.copy(
    orgnummer = VIRKSOMHETSNUMMER_NO_PLANLAGTMOTE.value
)

class ModiasyforestMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val server = mockModiasyforestServer(
        port
    )

    private fun mockModiasyforestServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            routing {
                get(PERSON_NARMESTELEDER_PATH) {
                    if (getPersonIdentHeader() == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(emptyList<NarmesteLederDTO>())
                    } else {
                        call.respond(
                            listOf(
                                narmesteLederDTOVirksomhetHasLeader
                            )
                        )
                    }
                }
            }
        }
    }
}
