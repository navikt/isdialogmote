package no.nav.syfo.testhelper.mock

import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.client.azuread.AadAccessToken
import java.nio.file.Paths

fun wellKnownMock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        authorization_endpoint = "authorizationendpoint",
        token_endpoint = "tokenendpoint",
        jwks_uri = uri.toString(),
        issuer = "https://sts.issuer.net/myid"
    )
}

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

class AzureADMock {
    val aadAccessToken = AadAccessToken(
        access_token = "token",
        expires_in = 3600,
        token_type = "type"
    )
}
