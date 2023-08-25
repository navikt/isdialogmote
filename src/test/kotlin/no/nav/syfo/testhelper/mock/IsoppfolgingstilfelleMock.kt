package no.nav.syfo.testhelper.mock

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.oppfolgingstilfelle.ARBEIDSGIVERPERIODE_DAYS
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader
import java.time.LocalDate

fun oppfolgingstilfellePersonDTO(
    personIdent: PersonIdent = ARBEIDSTAKER_FNR,
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

fun oppfolgingstilfellePersonDTONoTilfelle(
    personIdent: PersonIdent,
) = OppfolgingstilfellePersonDTO(
    personIdent = personIdent.value,
    oppfolgingstilfelleList = emptyList()
)

fun oppfolgingstilfelleDTOOverlappingOTList() = listOf(
    OppfolgingstilfelleDTO(
        arbeidstakerAtTilfelleEnd = true,
        start = LocalDate.now().minusMonths(8L),
        end = LocalDate.now().minusMonths(3L),
        virksomhetsnummerList = listOf(
            VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
        )
    )
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
                call.respond(
                    when (getPersonIdentHeader()) {
                        ARBEIDSTAKER_FNR.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FNR)
                        ARBEIDSTAKER_ANNEN_FNR.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_ANNEN_FNR)
                        ARBEIDSTAKER_TREDJE_FNR.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_TREDJE_FNR)
                        ARBEIDSTAKER_FJERDE_FNR.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FJERDE_FNR)
                        ARBEIDSTAKER_NO_JOURNALFORING.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_NO_JOURNALFORING)
                        ARBEIDSTAKER_IKKE_VARSEL.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_IKKE_VARSEL)
                        ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value -> oppfolgingstilfellePersonDTO(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
                        ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value -> oppfolgingstilfellePersonDTONoTilfelle(ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE)
                        ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value ->
                            oppfolgingstilfellePersonDTO(
                                personIdent = ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE,
                                end = LocalDate.now().minusDays(ARBEIDSGIVERPERIODE_DAYS + 1),
                            )
                        else -> HttpStatusCode.InternalServerError
                    }
                )
            }
            get(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH) {
                when (getPersonIdentHeader()) {
                    ARBEIDSTAKER_FNR.value -> call.respond(oppfolgingstilfelleDTOOverlappingOTList())
                    else -> call.respond(emptyList<OppfolgingstilfelleDTO>())
                }
            }
        }
    }
}
