package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.common.mock.tilgangskontroll.MockTilgangResponse
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient.Companion.TILGANGSKONTROLL_BRUKERE_PATH
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient.Companion.TILGANGSKONTROLL_PERSON_PATH
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    return when {
        requestUrl.endsWith(TILGANGSKONTROLL_BRUKERE_PATH) -> {
            respondOk(
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
                ),
            )
        }

        requestUrl.contains(TILGANGSKONTROLL_PERSON_PATH) -> {
            val personident = request.headers[NAV_PERSONIDENT_HEADER]
            if (personident == ARBEIDSTAKER_VEILEDER_NO_ACCESS.value) {
                respondOk(
                    MockTilgangResponse(
                        erGodkjent = false,
                        fullTilgang = false,
                    ),
                )
            } else {
                respondOk(
                    MockTilgangResponse(
                        erGodkjent = true,
                        fullTilgang = true,
                    ),
                )
            }
        }

        else -> {
            error("Unhandled $requestUrl")
        }
    }
}
