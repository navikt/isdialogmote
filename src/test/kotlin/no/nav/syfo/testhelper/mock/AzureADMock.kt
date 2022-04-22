package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.azuread.AzureAdV2TokenResponse
import no.nav.syfo.testhelper.UserConstants.AZUREAD_TOKEN
import no.nav.syfo.testhelper.getRandomPort
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

class AzureAdV2Mock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val aadV2TokenResponse = AzureAdV2TokenResponse(
        access_token = AZUREAD_TOKEN,
        expires_in = 3600,
        token_type = "type"
    )

    val name = "azureadv2"
    val server = mockAzureAdV2Server(port = port)

    private fun mockAzureAdV2Server(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post {
                    call.respond(aadV2TokenResponse)
                }
            }
        }
    }
}
