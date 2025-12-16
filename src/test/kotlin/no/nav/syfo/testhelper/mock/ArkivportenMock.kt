package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenDocumentRequestDTO
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.dokumentportenMock(request: HttpRequestData): HttpResponseData {
    val dokumentportenDocument = request.receiveBody<DokumentportenDocumentRequestDTO>()
    return when {
        dokumentportenDocument.orgNumber == UserConstants.VIRKSOMHETSNUMMER_DOKUMENTPORTEN_FAILS.value ->
            respond("", HttpStatusCode.InternalServerError)
        else -> respond("", HttpStatusCode.OK)
    }
}
