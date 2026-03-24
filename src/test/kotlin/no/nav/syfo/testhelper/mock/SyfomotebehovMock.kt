package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*

suspend fun MockRequestHandleScope.syfomotebehovMock(request: HttpRequestData): HttpResponseData {
    return respond("", HttpStatusCode.OK)
}
