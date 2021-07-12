package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient.Companion.SYKMELDT_FNR_HEADER
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.narmesteleder.Tilgang
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.PERSON_TLF
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import java.time.LocalDate
import java.time.OffsetDateTime

val tilgang = listOf(Tilgang.MOTE)

val narmesteLeder = NarmesteLederDTO(
    fnr = ARBEIDSTAKER_FNR.value,
    narmesteLederFnr = NARMESTELEDER_FNR.value,
    orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    narmesteLederEpost = "narmesteLederNavn@gmail.com",
    narmesteLederTelefonnummer = PERSON_TLF,
    aktivFom = LocalDate.now(),
    tilganger = tilgang,
    timestamp = OffsetDateTime.now(),
    arbeidsgiverForskutterer = true,
    navn = "narmesteLederNavn",
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
                get(NarmesteLederClient.CURRENT_NARMESTELEDER_PATH) {
                    if (call.request.headers[SYKMELDT_FNR_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(NarmesteLederRelasjonDTO(narmesteLeder))
                    }
                }
                get(NarmesteLederClient.NARMESTELEDERE_PATH) {
                    call.respond(listOf(narmesteLeder))
                }
                get(NarmesteLederClient.CURRENT_ANSATTE_PATH) {
                    call.respond(
                        listOf(
                            narmesteLeder,
                            narmesteLeder.copy(orgnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value)
                        )
                    )
                }
            }
        }
    }
}
