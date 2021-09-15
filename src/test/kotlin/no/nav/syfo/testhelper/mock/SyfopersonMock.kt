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
import no.nav.syfo.client.person.kontaktinfo.*
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.PERSON_EMAIL
import no.nav.syfo.testhelper.UserConstants.PERSON_TLF
import no.nav.syfo.testhelper.getRandomPort
import no.nav.syfo.util.getPersonIdentHeader
import java.time.LocalDate

fun digitalKontaktinfoBolkKanVarslesTrue(personIdentNumber: String) = DigitalKontaktinfoBolk(
    kontaktinfo = mapOf(
        personIdentNumber to DigitalKontaktinfo(
            epostadresse = PERSON_EMAIL,
            kanVarsles = true,
            reservert = false,
            mobiltelefonnummer = PERSON_TLF,
            personident = personIdentNumber
        )
    )
)

val digitalKontaktinfoBolkKanVarslesFalse = DigitalKontaktinfoBolk(
    kontaktinfo = mapOf(
        ARBEIDSTAKER_IKKE_VARSEL.value to DigitalKontaktinfo(
            epostadresse = PERSON_EMAIL,
            kanVarsles = false,
            reservert = true,
            mobiltelefonnummer = PERSON_TLF,
            personident = ARBEIDSTAKER_IKKE_VARSEL.value
        )
    )
)

val oppfolgingstilfellePersonDTO = OppfolgingstilfellePersonDTO(
    fom = LocalDate.now().minusDays(10),
    tom = LocalDate.now().plusDays(10),
)

class SyfopersonMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "syfoperson"
    val server = mockPersonServer(
        port,
    )

    private fun mockPersonServer(
        port: Int,
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
                get(KontaktinformasjonClient.PERSON_V2_KONTAKTINFORMASJON_PATH) {
                    if (getPersonIdentHeader() == ARBEIDSTAKER_IKKE_VARSEL.value) {
                        call.respond(digitalKontaktinfoBolkKanVarslesFalse)
                    } else {
                        call.respond(digitalKontaktinfoBolkKanVarslesTrue(getPersonIdentHeader()!!))
                    }
                }
                get(OppfolgingstilfelleClient.PERSON_V2_OPPFOLGINGSTILFELLE_PATH) {
                    call.respond(oppfolgingstilfellePersonDTO)
                }
            }
        }
    }
}
