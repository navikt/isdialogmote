package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import no.nav.syfo.common.mock.tilgangskontroll.mockTilgangskontrollRequestHandler

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData =
    mockTilgangskontrollRequestHandler(request, mockTilgangDetailsPerNavident)
