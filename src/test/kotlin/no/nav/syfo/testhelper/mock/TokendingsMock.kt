package no.nav.syfo.testhelper.mock

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.Paths
import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.tokendings.TokenendingsTokenDTO
import no.nav.syfo.testhelper.getRandomPort

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

class TokendingsMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val tokenResponse = TokenendingsTokenDTO(
        access_token = "token",
        issued_token_type = "issued_token_type",
        token_type = "token_type",
        expires_in = 3600,
    )

    val name = "tokendings"
    val server = mockTokendingsServer(port = port)

    private fun mockTokendingsServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post {
                    call.respond(tokenResponse)
                }
            }
        }
    }
}
