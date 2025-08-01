package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2TokenResponse
import no.nav.syfo.testhelper.UserConstants.AZUREAD_TOKEN
import java.nio.file.Paths

fun wellKnownVeilederV2Mock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        authorization_endpoint = "authorizationendpoint",
        token_endpoint = "tokenendpoint",
        jwks_uri = uri.toString(),
        issuer = "https://sts.issuer.net/veileder/v2"
    )
}

fun MockRequestHandleScope.azureAdMockResponse(): HttpResponseData = respondOk(
    AzureAdV2TokenResponse(
        access_token = AZUREAD_TOKEN,
        expires_in = 3600,
        token_type = "type",
    )
)
