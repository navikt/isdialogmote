package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenDocument
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.arkivportenMock(request: HttpRequestData): HttpResponseData {
    val arkivportenDocument = request.receiveBody<ArkivportenDocument>()
    return when {
        arkivportenDocument.orgNumber == UserConstants.VIRKSOMHETSNUMMER_ARKIVPORTEN_FAILS.value ->
            respond("", HttpStatusCode.InternalServerError)
        else -> respond("", HttpStatusCode.OK)
    }
}
