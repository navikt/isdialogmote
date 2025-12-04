package no.nav.syfo.testhelper.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.Environment
import no.nav.syfo.api.authentication.configure

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.aadTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.tokenxEndpoint}") -> tokendingsMock()
                requestUrl.startsWith("/${environment.dokarkivUrl}") -> dokarkivMock(request)
                requestUrl.startsWith("/${environment.istilgangskontrollUrl}") -> tilgangskontrollResponse(
                    request
                )
                requestUrl.startsWith("/${environment.syfobehandlendeenhetUrl}") -> {
                    getBehandlendeEnhetResponse(request)
                }
                requestUrl.startsWith("/${environment.narmestelederUrl}") -> narmestelederMock(request)
                requestUrl.startsWith("/${environment.isoppfolgingstilfelleUrl}") -> oppfolgingstilfelleMockResponse(request)
                requestUrl.startsWith("/${environment.krrUrl}") -> krrMock(request)
                requestUrl.startsWith("/${environment.pdlUrl}") -> pdlMockResponse(request)
                requestUrl.startsWith("/${environment.eregUrl}") -> eregMockResponse(request)
                requestUrl.startsWith("/${environment.ispdfgenUrl}") -> pdfGenMockResponse(request)
                requestUrl.startsWith("/${environment.dialogmeldingUrl}") -> getBehandlerResponse(request)
                requestUrl.startsWith("/${environment.arkivportenUrl}") -> arkivportenMock(request)

                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
