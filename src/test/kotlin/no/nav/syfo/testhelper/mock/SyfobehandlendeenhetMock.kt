package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.behandlendeenhet.BehandlendeEnhetDTO
import no.nav.syfo.infrastructure.client.behandlendeenhet.EnhetDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER

val mockBehandlendeEnhetDTO = BehandlendeEnhetDTO(
    geografiskEnhet = EnhetDTO(
        enhetId = ENHET_NR.value,
        navn = "enhet",
    ),
    oppfolgingsenhetDTO = null,
)

fun MockRequestHandleScope.getBehandlendeEnhetResponse(request: HttpRequestData): HttpResponseData {
    val personident = request.headers[NAV_PERSONIDENT_HEADER]
    return when (personident) {
        ARBEIDSTAKER_FNR.value,
        ARBEIDSTAKER_ANNEN_FNR.value,
        ARBEIDSTAKER_TREDJE_FNR.value,
        ARBEIDSTAKER_FJERDE_FNR.value,
        ARBEIDSTAKER_NO_JOURNALFORING.value,
        ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value,
        ARBEIDSTAKER_IKKE_VARSEL.value,
        ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value,
        ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value -> respondOk(mockBehandlendeEnhetDTO)
        ARBEIDSTAKER_NO_BEHANDLENDE_ENHET.value -> respond("", HttpStatusCode.NoContent)
        else -> respondError(HttpStatusCode.InternalServerError)
    }
}
