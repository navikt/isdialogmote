package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.narmesteleder.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.PERSON_TLF
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import java.time.LocalDate
import java.time.OffsetDateTime

val tilgang = listOf(Tilgang.MOTE)

val narmesteLeder =
    NarmesteLederRelasjonDTO(
        NarmesteLederDTO(
            fnr = ARBEIDSTAKER_FNR.value,
            narmesteLederFnr = ARBEIDSTAKER_FNR.value.reversed(),
            orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
            narmesteLederEpost = "narmesteLederNavn@gmail.com",
            narmesteLederTelefonnummer = PERSON_TLF,
            aktivFom = LocalDate.now(),
            tilganger = tilgang,
            timestamp = OffsetDateTime.now(),
            arbeidsgiverForskutterer = true,
            navn = "narmesteLederNavn",
        )
    )

class NarmesteLederMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "narmesteleder"
    val server = mockNarmesteLederServer(port)

    private fun mockNarmesteLederServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(NarmesteLederClient.NARMESTELEDER_CURRENT_PATH) {
                    if (call.request.headers["Sykmeldt-Fnr"] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(narmesteLeder)
                    }
                }
            }
        }
    }
}
