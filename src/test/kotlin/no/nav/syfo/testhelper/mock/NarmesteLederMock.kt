package no.nav.syfo.testhelper.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonStatus
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR_2
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.PERSON_TLF
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER

val narmesteLeder = NarmesteLederRelasjonDTO(
    uuid = UUID.randomUUID().toString(),
    arbeidstakerPersonIdentNumber = ARBEIDSTAKER_FNR.value,
    narmesteLederPersonIdentNumber = NARMESTELEDER_FNR.value,
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    virksomhetsnavn = "Virksomhetsnavn",
    narmesteLederEpost = "narmesteLederNavn@gmail.com",
    narmesteLederTelefonnummer = PERSON_TLF,
    aktivFom = LocalDate.now(),
    aktivTom = null,
    timestamp = LocalDateTime.now(),
    arbeidsgiverForskutterer = true,
    narmesteLederNavn = "narmesteLederNavn",
    status = NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name,
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
                    if (call.request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(
                            listOf(
                                narmesteLeder,
                                narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                            )
                        )
                    }
                }
                get(NarmesteLederClient.NARMESTELEDERE_SELVBETJENING_PATH) {
                    if (call.request.headers[NAV_PERSONIDENT_HEADER] == NARMESTELEDER_FNR_2.value) {
                        call.respond(emptyList<NarmesteLederRelasjonDTO>())
                    } else {
                        call.respond(
                            listOf(
                                narmesteLeder,
                                narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                            )
                        )
                    }
                }
            }
        }
    }
}
