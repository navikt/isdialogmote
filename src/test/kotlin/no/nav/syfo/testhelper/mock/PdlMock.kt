package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.pdl.PdlRequest
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generatePdlError
import no.nav.syfo.testhelper.generator.generatePdlIdenter
import no.nav.syfo.testhelper.generator.generatePdlPersonResponse

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlRequest>()
    val isHentIdenterRequest = pdlRequest.toString().contains("hentIdenter")
    return if (isHentIdenterRequest) {
        when (pdlRequest.variables.ident) {
            UserConstants.ARBEIDSTAKER_TREDJE_FNR.value -> {
                respondOk(
                    generatePdlIdenter(
                        UserConstants.ARBEIDSTAKER_TREDJE_FNR.value,
                        UserConstants.ARBEIDSTAKER_FJERDE_FNR.value,
                    )
                )
            }
            UserConstants.ARBEIDSTAKER_IKKE_AKTIVT_FNR.value -> {
                respondOk(generatePdlIdenter("dummyIdent"))
            }
            UserConstants.ARBEIDSTAKER_WITH_ERROR_FNR.value -> {
                respondOk(
                    generatePdlIdenter(pdlRequest.variables.ident)
                        .copy(errors = generatePdlError(code = "not_found"))
                )
            }
            else -> {
                respondOk(generatePdlIdenter(pdlRequest.variables.ident))
            }
        }
    } else {
        respondOk(generatePdlPersonResponse())
    }
}
