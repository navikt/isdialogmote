package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonStatus
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR_2
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.PERSON_TLF
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

val narmesteLeder = NarmesteLederRelasjonDTO(
    uuid = UUID.randomUUID().toString(),
    arbeidstakerPersonIdentNumber = ARBEIDSTAKER_FNR.value,
    narmesteLederPersonIdentNumber = NARMESTELEDER_FNR.value,
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    virksomhetsnavn = "Virksomhetsnavn",
    narmesteLederEpost = "narmesteLederNavn@gmail.com",
    narmesteLederTelefonnummer = PERSON_TLF,
    aktivFom = LocalDate.now(),
    aktivTom = null,
    timestamp = LocalDateTime.now(),
    arbeidsgiverForskutterer = true,
    narmesteLederNavn = "narmesteLederNavn",
    status = NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name,
)

fun MockRequestHandleScope.narmestelederMock(request: HttpRequestData): HttpResponseData {
    val personIdentNumber = request.headers[NAV_PERSONIDENT_HEADER]
    val requestUrl = request.url.encodedPath
    return when {
        requestUrl.endsWith(NarmesteLederClient.CURRENT_NARMESTELEDER_PATH) -> {
            when (personIdentNumber) {
                ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value -> {
                    respondOk(emptyList<NarmesteLederRelasjonDTO>())
                }
                else -> {
                    respondOk(
                        listOf(
                            narmesteLeder,
                            narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                        )
                    )
                }
            }
        }
        requestUrl.endsWith(NarmesteLederClient.NARMESTELEDERE_SELVBETJENING_PATH) -> {
            when (personIdentNumber) {
                NARMESTELEDER_FNR_2.value -> {
                    respondOk(emptyList<NarmesteLederRelasjonDTO>())
                }
                else -> {
                    respondOk(
                        listOf(
                            narmesteLeder,
                            narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                        )
                    )
                }
            }
        }
        else -> error("Unhandled $requestUrl")
    }
}
