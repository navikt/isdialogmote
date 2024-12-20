package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING

suspend fun MockRequestHandleScope.dokarkivMock(request: HttpRequestData): HttpResponseData {
    val journalpostResponse = JournalpostResponse(
        journalpostId = UserConstants.JOURNALPOSTID_JOURNALFORING,
        journalstatus = "journalstatus",
    )
    val journalpostRequest = request.receiveBody<JournalpostRequest>()
    return when {
        journalpostRequest.bruker?.id == ARBEIDSTAKER_NO_JOURNALFORING.value -> respondError(HttpStatusCode.InternalServerError)
        journalpostRequest.sak.sakstype.trim().isEmpty() -> respondError(HttpStatusCode.BadRequest)
        journalpostRequest.eksternReferanseId == UserConstants.EXISTING_EKSTERN_REFERANSE_UUID.toString() -> respondConflict(journalpostResponse)
        else -> respondOk(journalpostResponse)
    }
}
