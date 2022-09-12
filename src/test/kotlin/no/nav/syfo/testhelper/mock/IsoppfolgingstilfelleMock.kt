package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.oppfolgingstilfelle.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader
import java.time.LocalDate

fun oppfolgingstilfellePersonDTO(
    personIdent: PersonIdentNumber = ARBEIDSTAKER_FNR,
    end: LocalDate = LocalDate.now().plusDays(10),
) = OppfolgingstilfellePersonDTO(
    personIdent = personIdent.value,
    oppfolgingstilfelleList = listOf(
        OppfolgingstilfelleDTO(
            arbeidstakerAtTilfelleEnd = true,
            start = LocalDate.now().minusDays(10),
            end = end,
            virksomhetsnummerList = listOf(
                VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
            ),
        ),
    ),
)

class IsoppfolgingstilfelleMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "isoppfolgingstilfelle"

    val server = embeddedServer(
        factory = Netty,
        port = port
    ) {
        installContentNegotiation()
        routing {
            get(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH) {
                when (getPersonIdentHeader()) {
                    ARBEIDSTAKER_FNR.value -> call.respond(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FNR))
                    ARBEIDSTAKER_ADRESSEBESKYTTET.value -> call.respond(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_ADRESSEBESKYTTET))
                    ARBEIDSTAKER_ANNEN_FNR.value -> call.respond(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_ANNEN_FNR))
                    ARBEIDSTAKER_NO_JOURNALFORING.value -> call.respond(
                        oppfolgingstilfellePersonDTO(
                            ARBEIDSTAKER_NO_JOURNALFORING
                        )
                    )
                    ARBEIDSTAKER_IKKE_VARSEL.value -> call.respond(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_IKKE_VARSEL))
                    ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value -> call.respond(
                        oppfolgingstilfellePersonDTO(
                            personIdent = ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE,
                            end = LocalDate.now().minusDays(ARBEIDSGIVERPERIODE_DAYS + 1),
                        )
                    )
                    ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value -> call.respond(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER))
                    else -> call.respond(HttpStatusCode.InternalServerError, "")
                }
            }
        }
    }
}
