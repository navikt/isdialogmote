package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangEnhetClient.Companion.TILGANGSKONTROLL_ENHET_PATH
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
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS

private const val TILGANGSKONTROLL_PERSON_PATH = "/api/tilgang/navident/person"
private const val TILGANGSKONTROLL_PERSON_LIST_PATH = "/api/tilgang/navident/brukere"

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

        requestUrl.endsWith("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR.value}") ->
            respondOk(mapOf("erGodkjent" to true))

        requestUrl.endsWith("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR_NO_ACCESS.value}") ->
            respondOk(mapOf("erGodkjent" to false))

        requestUrl.contains(TILGANGSKONTROLL_PERSON_PATH) -> {
            val personident = request.headers[NAV_PERSONIDENT_HEADER]
            if (personident == ARBEIDSTAKER_VEILEDER_NO_ACCESS.value) {
                respondOk(mapOf("erGodkjent" to false))
            } else {
                respondOk(mapOf("erGodkjent" to true))
            }
        }

        else -> error("Unhandled $requestUrl")
    }
}
