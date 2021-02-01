package no.nav.syfo.testhelper.mock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseResponse
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader

class SyfopersonMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val beskyttetFalse = AdressebeskyttelseResponse(
        beskyttet = false,
    )
    val beskyttetTrue = AdressebeskyttelseResponse(
        beskyttet = true,
    )

    val server = mockPersonServer(
        port,
        beskyttetFalse,
        beskyttetTrue
    )

    private fun mockPersonServer(
        port: Int,
        beskyttetFalse: AdressebeskyttelseResponse,
        beskyttetTrue: AdressebeskyttelseResponse,
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
                get(AdressebeskyttelseClient.PERSON_ADRESSEBESKYTTELSE_PATH) {
                    if (getPersonIdentHeader() == ARBEIDSTAKER_ADRESSEBESKYTTET.value) {
                        call.respond(beskyttetTrue)
                    } else {
                        call.respond(beskyttetFalse)
                    }
                }
            }
        }
    }
}
