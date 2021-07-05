package no.nav.syfo.testhelper.mock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_JOURNALFORING
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.getRandomPort

class VeilederTilgangskontrollMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val tilgangFalse = Tilgang(
        false,
        ""
    )
    val tilgangTrue = Tilgang(
        true,
        ""
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
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            routing {
                post("/syfo-tilgangskontroll/api/tilgang/brukere") {
                    call.respond(
                        listOf(
                            ARBEIDSTAKER_FNR.value,
                            ARBEIDSTAKER_ANNEN_FNR.value,
                            ARBEIDSTAKER_ADRESSEBESKYTTET.value,
                        )
                    )
                }
                get("/syfo-tilgangskontroll/api/tilgang/bruker") {
                    if (ARBEIDSTAKER_VEILEDER_NO_ACCESS.value == call.parameters["fnr"]) {
                        call.respond(HttpStatusCode.Forbidden, tilgangFalse)
                    } else {
                        call.respond(tilgangTrue)
                    }
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_VEILEDER_NO_ACCESS.value}") {
                    call.respond(HttpStatusCode.Forbidden, tilgangFalse)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_FNR.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_ANNEN_FNR.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_ADRESSEBESKYTTET.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_NO_JOURNALFORING.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_IKKE_VARSEL.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/bruker/${ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value}") {
                    call.respond(tilgangTrue)
                }
                post("/syfo-tilgangskontroll/api/tilgang/navident/brukere") {
                    call.respond(
                        listOf(
                            ARBEIDSTAKER_FNR.value,
                            ARBEIDSTAKER_ANNEN_FNR.value,
                            ARBEIDSTAKER_ADRESSEBESKYTTET.value,
                        )
                    )
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/enhet/${ENHET_NR.value}") {
                    call.respond(tilgangTrue)
                }
                get("/syfo-tilgangskontroll/api/tilgang/navident/enhet/${ENHET_NR_NO_ACCESS.value}") {
                    call.respond(HttpStatusCode.Forbidden, tilgangFalse)
                }
            }
        }
    }
}
