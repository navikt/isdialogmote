package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient.Companion.ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.ARBEIDSGIVERPERIODE_DAYS
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
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
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

fun MockRequestHandleScope.oppfolgingstilfelleMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    val personIdent = request.headers[NAV_PERSONIDENT_HEADER]
    return when {
        requestUrl.endsWith(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH) -> {
            when (personIdent) {
                ARBEIDSTAKER_FNR.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FNR))
                ARBEIDSTAKER_ANNEN_FNR.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_ANNEN_FNR))
                ARBEIDSTAKER_TREDJE_FNR.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_TREDJE_FNR))
                ARBEIDSTAKER_FJERDE_FNR.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FJERDE_FNR))
                ARBEIDSTAKER_NO_JOURNALFORING.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_NO_JOURNALFORING))
                ARBEIDSTAKER_IKKE_VARSEL.value -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_IKKE_VARSEL))
                ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value -> respondOk(
                    oppfolgingstilfellePersonDTO(
                        ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
                    )
                )
                ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value -> respondOk(
                    oppfolgingstilfellePersonDTONoTilfelle(
                        ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
                    )
                )
                ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value -> respondOk(
                    oppfolgingstilfellePersonDTO(
                        personIdent = ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE,
                        end = LocalDate.now().minusDays(ARBEIDSGIVERPERIODE_DAYS + 1),
                    )
                )
                else -> respondError(HttpStatusCode.InternalServerError)
            }
        }
        requestUrl.endsWith(ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH) -> {
            when (personIdent) {
                ARBEIDSTAKER_FNR.value -> respondOk(oppfolgingstilfelleDTOOverlappingOTList())
                else -> respondOk(emptyList<OppfolgingstilfelleDTO>())
            }
        }
        else -> respondOk(oppfolgingstilfellePersonDTO(ARBEIDSTAKER_FNR))
    }
}
