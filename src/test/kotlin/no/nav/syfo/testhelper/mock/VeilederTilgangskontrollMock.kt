package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_ENHET_PATH
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_PERSON_LIST_PATH
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.getRandomPort

class VeilederTilgangskontrollMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val tilgangFalse = Tilgang(
        erGodkjent = false,
    )
    val tilgangTrue = Tilgang(
        erGodkjent = true,
    )

    val name = "veiledertilgangskontroll"
    val server = mockTilgangServer(
        port,
        tilgangFalse,
        tilgangTrue
    )

    private fun mockTilgangServer(
        port: Int,
        tilgangFalse: Tilgang,
        tilgangTrue: Tilgang,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(TILGANGSKONTROLL_PERSON_LIST_PATH) {
                    call.respond(
                        listOf(
                            ARBEIDSTAKER_FNR.value,
                            ARBEIDSTAKER_ANNEN_FNR.value,
                            ARBEIDSTAKER_TREDJE_FNR.value,
                            ARBEIDSTAKER_FJERDE_FNR.value,
                            ARBEIDSTAKER_NO_JOURNALFORING.value,
                            ARBEIDSTAKER_IKKE_VARSEL.value,
                            ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value,
                            ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value,
                            ARBEIDSTAKER_NO_BEHANDLENDE_ENHET.value,
                            ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value,
                        )
                    )
                }
                get("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR.value}") {
                    call.respond(tilgangTrue)
                }
                get("$TILGANGSKONTROLL_ENHET_PATH/${ENHET_NR_NO_ACCESS.value}") {
                    call.respond(HttpStatusCode.Forbidden, tilgangFalse)
                }
            }
        }
    }
}
