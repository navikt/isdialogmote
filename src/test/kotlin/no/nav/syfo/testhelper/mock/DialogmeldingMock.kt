package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.dialogmelding.BehandlerDTO
import no.nav.syfo.testhelper.UserConstants

val mockBehandlerDTO = BehandlerDTO(
    type = "FASTLEGE",
    behandlerRef = UserConstants.BEHANDLER_REF,
    kategori = "LE",
    fnr = null,
    hprId = UserConstants.BEHANDLER_HPRID,
    herId = null,
    fornavn = "Fornavn",
    mellomnavn = null,
    etternavn = "Etternavn",
    orgnummer = null,
    kontor = null,
    kontorHerId = null,
    kontorDialogmeldingmeldingEnabled = true,
    adresse = null,
    postnummer = null,
    poststed = null,
    telefon = null,
    invalidated = false,
)

fun MockRequestHandleScope.getBehandlerResponse(request: HttpRequestData): HttpResponseData =
    respondOk(mockBehandlerDTO)
