package no.nav.syfo.testhelper.mock

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.client.person.kontaktinfo.DigitalKontaktinfo
import no.nav.syfo.client.person.kontaktinfo.DigitalKontaktinfoBolk
import no.nav.syfo.client.person.kontaktinfo.DigitalKontaktinfoBolkRequestBody
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.getRandomPort

fun digitalKontaktinfoBolkKanVarslesTrue(
    personIdent: String,
) = DigitalKontaktinfoBolk(
    personer = mapOf(
        personIdent to DigitalKontaktinfo(
            epostadresse = UserConstants.PERSON_EMAIL,
            kanVarsles = true,
            reservert = false,
            mobiltelefonnummer = UserConstants.PERSON_TLF,
            aktiv = true,
            personident = personIdent,
        )
    )
)

val digitalKontaktinfoBolkKanVarslesFalse = DigitalKontaktinfoBolk(
    personer = mapOf(
        UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value to DigitalKontaktinfo(
            epostadresse = UserConstants.PERSON_EMAIL,
            kanVarsles = false,
            reservert = true,
            mobiltelefonnummer = UserConstants.PERSON_TLF,
            aktiv = true,
            personident = UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value,
        )
    )
)

val digitalKontaktinfoBolkFeil = DigitalKontaktinfoBolk(
    feil = mapOf(UserConstants.ARBEIDSTAKER_DKIF_FEIL.value to "det skjedde en feil")
)

class KrrMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "krr"
    val server = embeddedServer(
        factory = Netty,
        port = port
    ) {
        installContentNegotiation()
        routing {
            post(KontaktinformasjonClient.KRR_KONTAKTINFORMASJON_BOLK_PATH) {
                val krrRequestBodyPersonIdent = call.receive<DigitalKontaktinfoBolkRequestBody>().personidenter.first()
                if (krrRequestBodyPersonIdent == UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value) {
                    call.respond(digitalKontaktinfoBolkKanVarslesFalse)
                } else if (krrRequestBodyPersonIdent == UserConstants.ARBEIDSTAKER_DKIF_FEIL.value) {
                    call.respond(digitalKontaktinfoBolkFeil)
                } else {
                    call.respond(digitalKontaktinfoBolkKanVarslesTrue(krrRequestBodyPersonIdent))
                }
            }
        }
    }
}
