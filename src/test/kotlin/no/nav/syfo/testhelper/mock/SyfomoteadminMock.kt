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
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient.Companion.PLANLAGTMOTE_BEKREFT_PATH
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient.Companion.PLANLAGTMOTE_PATH
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_AKTORID
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.getRandomPort
import java.time.LocalDateTime
import java.util.*

val planlagtTidOgStedDTO = PlanlagtTidOgStedDTO(
    id = 0,
    tid = LocalDateTime.now().plusDays(1),
    created = LocalDateTime.now().minusDays(1),
    sted = "Hos NAV-Kontor",
    valgt = true,
)

val planlagtMoteDeltakerDTOArbeidsgiver = PlanlagtMoteDeltakerDTO(
    deltakerUuid = UUID.randomUUID().toString(),
    type = PlanlagtMoteDeltakerType.ARBEIDSGIVER.value,
    svar = emptyList(),
    orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
)

val planlagtMoteDeltakerDTOArbeidstaker = PlanlagtMoteDeltakerDTO(
    deltakerUuid = UUID.randomUUID().toString(),
    type = PlanlagtMoteDeltakerType.ARBEIDSTAKER.value,
    svar = emptyList(),
)

fun planlagtMoteDTO(personIdentNumber: PersonIdentNumber) = PlanlagtMoteDTO(
    moteUuid = UUID.randomUUID().toString(),
    opprettetAv = VEILEDER_IDENT,
    aktorId = ARBEIDSTAKER_AKTORID,
    status = PlanlagtMoteStatus.OPPRETTET.name,
    fnr = personIdentNumber.value,
    opprettetTidspunkt = LocalDateTime.now().minusDays(1),
    navEnhet = ENHET_NR,
    eier = VEILEDER_IDENT,
    deltakere = listOf(
        planlagtMoteDeltakerDTOArbeidstaker,
        planlagtMoteDeltakerDTOArbeidsgiver,
    ),
    alternativer = listOf(
        planlagtTidOgStedDTO,
    ),
)

class SyfomoteadminMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val personIdentMoteMap = mapOf(
        ARBEIDSTAKER_FNR.value to planlagtMoteDTO(ARBEIDSTAKER_FNR),
        ARBEIDSTAKER_VEILEDER_NO_ACCESS.value to planlagtMoteDTO(ARBEIDSTAKER_VEILEDER_NO_ACCESS),
        ARBEIDSTAKER_ADRESSEBESKYTTET.value to planlagtMoteDTO(ARBEIDSTAKER_ADRESSEBESKYTTET),
        ARBEIDSTAKER_IKKE_VARSEL.value to planlagtMoteDTO(ARBEIDSTAKER_IKKE_VARSEL),
        ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value to planlagtMoteDTO(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
    )

    val server = mockPersonServer(
        port,
        personIdentMoteMap
    )

    private fun mockPersonServer(
        port: Int,
        personIdentMoteMap: Map<String, PlanlagtMoteDTO>,
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
            personIdentMoteMap.forEach { personIdentMote ->
                routing {
                    get("$PLANLAGTMOTE_PATH/${personIdentMote.value.moteUuid}") {
                        call.respond(personIdentMote.value)
                    }
                    post("$PLANLAGTMOTE_PATH/${personIdentMote.value.moteUuid}/$PLANLAGTMOTE_BEKREFT_PATH") {
                        if (call.parameters["varsle"] == "false") {
                            call.respond(HttpStatusCode.OK, "")
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "")
                        }
                    }
                }
            }
        }
    }
}
