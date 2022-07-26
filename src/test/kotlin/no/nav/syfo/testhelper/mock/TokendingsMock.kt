package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.tokendings.TokenendingsTokenDTO
import no.nav.syfo.testhelper.getRandomPort
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
