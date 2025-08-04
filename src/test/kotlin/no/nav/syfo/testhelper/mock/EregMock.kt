package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.ereg.EregOrganisasjonNavn
import no.nav.syfo.infrastructure.client.ereg.EregOrganisasjonResponse
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_EREG_FAILS
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER

fun MockRequestHandleScope.eregMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value) -> respondOk(
            EregOrganisasjonResponse(
                EregOrganisasjonNavn(
                    "Butikken",
                    ""
                )
            )
        )
        requestUrl.endsWith(VIRKSOMHETSNUMMER_EREG_FAILS.value) -> respondError(HttpStatusCode.InternalServerError)
        else -> error("Unhandled path $requestUrl")
    }
}
