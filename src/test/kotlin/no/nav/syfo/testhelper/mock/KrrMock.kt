package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.person.kontaktinfo.DigitalKontaktinfo
import no.nav.syfo.infrastructure.client.person.kontaktinfo.DigitalKontaktinfoBolk
import no.nav.syfo.infrastructure.client.person.kontaktinfo.DigitalKontaktinfoBolkRequestBody
import no.nav.syfo.testhelper.UserConstants

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

suspend fun MockRequestHandleScope.krrMock(request: HttpRequestData): HttpResponseData {
    val krrRequestBodyPersonIdent = request.receiveBody<DigitalKontaktinfoBolkRequestBody>().personidenter.first()
    return when (krrRequestBodyPersonIdent) {
        UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value -> {
            respondOk(digitalKontaktinfoBolkKanVarslesFalse)
        }
        UserConstants.ARBEIDSTAKER_DKIF_FEIL.value -> {
            respondOk(digitalKontaktinfoBolkFeil)
        }
        else -> {
            respondOk(digitalKontaktinfoBolkKanVarslesTrue(krrRequestBodyPersonIdent))
        }
    }
}
