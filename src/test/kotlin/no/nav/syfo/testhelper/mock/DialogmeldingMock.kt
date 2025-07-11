package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.dialogmelding.BehandlerDTO
import java.util.UUID

val mockBehandlerDTO = BehandlerDTO(
    behandlerRef = UUID.randomUUID().toString(),
    kategori = "LE",
    fnr = null,
    hprId = 123456,
    fornavn = "Fornavn",
    mellomnavn = null,
    etternavn = "Etternavn",
    orgnummer = null,
    kontor = null,
    adresse = null,
    postnummer = null,
    poststed = null,
    telefon = null,
)

fun MockRequestHandleScope.getBehandlerResponse(request: HttpRequestData): HttpResponseData =
    respondOk(mockBehandlerDTO)
