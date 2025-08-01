package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.infrastructure.client.tokendings.TokenendingsTokenDTO
import java.nio.file.Paths

fun wellKnownSelvbetjeningMock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        authorization_endpoint = "authorizationendpoint",
        token_endpoint = "tokenendpoint",
        jwks_uri = uri.toString(),
        issuer = "https://sts.issuer.net/myid"
    )
}

val tokenResponse = TokenendingsTokenDTO(
    access_token = "token",
    issued_token_type = "issued_token_type",
    token_type = "token_type",
    expires_in = 3600,
)

fun MockRequestHandleScope.tokendingsMock(): HttpResponseData = respondOk(tokenResponse)
