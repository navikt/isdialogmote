package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_ENHET_PATH
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_PERSON_LIST_PATH
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
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    return when {
        requestUrl.endsWith(TILGANGSKONTROLL_PERSON_LIST_PATH) -> respondOk(
            listOf(
                ARBEIDSTAKER_FNR.value,
                ARBEIDSTAKER_ANNEN_FNR.value,
                ARBEIDSTAKER_TREDJE_FNR.value,
                ARBEIDSTAKER_FJERDE_FNR.value,
                ARBEIDSTAKER_NO_JOURNALFORING.value,
                ARBEIDSTAKER_IKKE_VARSEL.value,
                ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value,
                ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value,
                ARBEIDSTAKER_NO_BEHANDLENDE_ENHET.value,
                ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value,
            )
        )

        requestUrl.endsWith("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR.value}") -> respondOk(
            Tilgang(
                erGodkjent = true,
            )
        )

        requestUrl.endsWith("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR_NO_ACCESS.value}") -> respondOk(
            Tilgang(erGodkjent = false)
        )

        else -> error("Unhandled $requestUrl")
    }
}
