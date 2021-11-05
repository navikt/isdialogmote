package no.nav.syfo.testhelper.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient.Companion.DISTRIBUER_JOURNALPOST_PATH
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonResponse
import no.nav.syfo.client.person.oppfolgingstilfelle.KOppfolgingstilfellePersonDTO
import no.nav.syfo.client.person.oppfolgingstilfelle.KSyketilfelledagDTO
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISPROXY_SYFOSYKETILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH
import no.nav.syfo.domain.AktorId
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_AKTORID
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_AKTORID
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL_AKTORID
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING_AKTORID
import no.nav.syfo.testhelper.getRandomPort
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun kOppfolgingstilfellePersonDTO(
    aktorId: AktorId = ARBEIDSTAKER_AKTORID,
) = KOppfolgingstilfellePersonDTO(
    aktorId = aktorId.value,
    tidslinje = listOf(
        KSyketilfelledagDTO(
            dag = LocalDate.now().minusDays(10),
            prioritertSyketilfellebit = null,
        ),
        KSyketilfelledagDTO(
            dag = LocalDate.now().plusDays(10),
            prioritertSyketilfellebit = null,
        ),
    ),
    sisteDagIArbeidsgiverperiode = KSyketilfelledagDTO(
        dag = LocalDate.now().plusDays(10),
        prioritertSyketilfellebit = null,
    ),
    antallBrukteDager = 0,
    oppbruktArbeidsgvierperiode = false,
    utsendelsestidspunkt = LocalDateTime.now(),
)

class IsproxyMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "isproxy"
    val server = mockIsproxyServer(port)

    private fun mockIsproxyServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(DISTRIBUER_JOURNALPOST_PATH) {
                    call.respond(JournalpostdistribusjonResponse(bestillingsId = UUID.randomUUID().toString()))
                }
                get("$ISPROXY_SYFOSYKETILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH/${ARBEIDSTAKER_AKTORID.value}") {
                    call.respond(kOppfolgingstilfellePersonDTO(ARBEIDSTAKER_AKTORID))
                }
                get("$ISPROXY_SYFOSYKETILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH/${ARBEIDSTAKER_ANNEN_AKTORID.value}") {
                    call.respond(kOppfolgingstilfellePersonDTO(ARBEIDSTAKER_ANNEN_AKTORID))
                }
                get("$ISPROXY_SYFOSYKETILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH/${ARBEIDSTAKER_NO_JOURNALFORING_AKTORID.value}") {
                    call.respond(kOppfolgingstilfellePersonDTO(ARBEIDSTAKER_NO_JOURNALFORING_AKTORID))
                }
                get("$ISPROXY_SYFOSYKETILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH/${ARBEIDSTAKER_IKKE_VARSEL_AKTORID.value}") {
                    call.respond(kOppfolgingstilfellePersonDTO(ARBEIDSTAKER_IKKE_VARSEL_AKTORID))
                }
            }
        }
    }
}
