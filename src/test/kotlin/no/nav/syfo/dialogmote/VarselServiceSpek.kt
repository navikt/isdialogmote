package no.nav.syfo.dialogmote

import io.mockk.*
import kotlinx.coroutines.DelicateCoroutinesApi
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.Unbounded
import no.nav.syfo.application.database.Database
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.altinn.createAltinnMelding
import no.nav.syfo.client.oppfolgingstilfelle.ARBEIDSGIVERPERIODE_DAYS
import no.nav.syfo.client.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_NO_NARMESTELEDER
import no.nav.syfo.testhelper.mock.narmesteLeder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

object VarselServiceSpek : Spek({

    describe(VarselServiceSpek::class.java.simpleName) {

        val arbeidstakerVarselService = mockk<ArbeidstakerVarselService>()
        val narmesteLederVarselService = mockk<NarmesteLederVarselService>()
        val behandlerVarselService = mockk<BehandlerVarselService>()
        val altinnClient = mockk<AltinnClient>()
        val oppfolgingstilfelleClient = mockk<OppfolgingstilfelleClient>()
        val database: Database = mockk()

        val varselService = VarselService(
            arbeidstakerVarselService = arbeidstakerVarselService,
            narmesteLederVarselService = narmesteLederVarselService,
            behandlerVarselService = behandlerVarselService,
            altinnClient = altinnClient,
            oppfolgingstilfelleClient = oppfolgingstilfelleClient,
            isAltinnSendingEnabled = true,
        )

        beforeEachTest {
            clearMocks(arbeidstakerVarselService)
            clearMocks(narmesteLederVarselService)
            clearMocks(behandlerVarselService)
            clearMocks(altinnClient)
            clearMocks(oppfolgingstilfelleClient)

            justRun { arbeidstakerVarselService.sendVarsel(any(), any(), any(), any(), any()) }
            justRun { narmesteLederVarselService.sendVarsel(any(), any()) }
            justRun { behandlerVarselService.sendVarsel(any(), any(), any(), any(), any(), any(), any(), any()) }
            justRun { altinnClient.sendToVirksomhet(any()) }
        }

        it("Send varsel to nærmeste leder") {
            coEvery { oppfolgingstilfelleClient.oppfolgingstilfellePerson(any(), any(), any()) } returns Oppfolgingstilfelle(
                start = LocalDate.MIN,
                end = LocalDate.MAX,
            )
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)
            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                true
            )

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Unbounded) {
                varselService.sendVarsel(
                    varselType = MotedeltakerVarselType.INNKALT,
                    isDigitalVarselEnabledForArbeidstaker = false,
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                    arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                    arbeidstakerbrevId = UUID.randomUUID(),
                    narmesteLeder = narmesteLeder,
                    virksomhetsbrevId = virksomhetsbrevId,
                    virksomhetsPdf = virksomhetsPdf,
                    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
                    behandlerId = null,
                    behandlerRef = null,
                    behandlerDocument = emptyList(),
                    behandlerPdf = byteArrayOf(0x2E, 0x38),
                    behandlerbrevId = null,
                    behandlerbrevParentId = null,
                    behandlerInnkallingUuid = null,
                    motetidspunkt = LocalDateTime.now().plusDays(1L),
                    token = "token",
                    callId = "callId",
                )

                verify(exactly = 1) { altinnClient.sendToVirksomhet(altinnMelding) }
                verify(exactly = 1) {
                    narmesteLederVarselService.sendVarsel(
                        narmesteLeder,
                        MotedeltakerVarselType.INNKALT
                    )
                }
            }
        }

        it("Send brev to Altinn when no nærmeste leder") {
            coEvery { oppfolgingstilfelleClient.oppfolgingstilfellePerson(any(), any(), any()) } returns Oppfolgingstilfelle(
                start = LocalDate.MIN,
                end = LocalDate.MAX,
            )
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)
            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                false
            )

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Unbounded) {
                varselService.sendVarsel(
                    varselType = MotedeltakerVarselType.INNKALT,
                    isDigitalVarselEnabledForArbeidstaker = false,
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                    arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                    arbeidstakerbrevId = UUID.randomUUID(),
                    narmesteLeder = null,
                    virksomhetsbrevId = virksomhetsbrevId,
                    virksomhetsPdf = virksomhetsPdf,
                    virksomhetsnummer = VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                    behandlerId = null,
                    behandlerRef = null,
                    behandlerDocument = emptyList(),
                    behandlerPdf = byteArrayOf(0x2E, 0x38),
                    behandlerbrevId = null,
                    behandlerbrevParentId = null,
                    behandlerInnkallingUuid = null,
                    motetidspunkt = LocalDateTime.now().plusDays(1L),
                    token = "token",
                    callId = "callId",
                )

                verify(exactly = 1) {
                    altinnClient.sendToVirksomhet(
                        altinnMelding
                    )
                }
                verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any()) }
            }
        }

        it("Send brev to Altinn, and not varsel to nærmeste leder when no active tilfelle") {
            coEvery { oppfolgingstilfelleClient.oppfolgingstilfellePerson(any(), any(), any()) } returns Oppfolgingstilfelle(
                start = LocalDate.MIN,
                end = LocalDate.now().minusDays(ARBEIDSGIVERPERIODE_DAYS + 1),
            )
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)
            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                false
            )

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Unbounded) {
                varselService.sendVarsel(
                    varselType = MotedeltakerVarselType.INNKALT,
                    isDigitalVarselEnabledForArbeidstaker = false,
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                    arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                    arbeidstakerbrevId = UUID.randomUUID(),
                    narmesteLeder = null,
                    virksomhetsbrevId = virksomhetsbrevId,
                    virksomhetsPdf = virksomhetsPdf,
                    virksomhetsnummer = VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                    behandlerId = null,
                    behandlerRef = null,
                    behandlerDocument = emptyList(),
                    behandlerPdf = byteArrayOf(0x2E, 0x38),
                    behandlerbrevId = null,
                    behandlerbrevParentId = null,
                    behandlerInnkallingUuid = null,
                    motetidspunkt = LocalDateTime.now().plusDays(1L),
                    token = "token",
                    callId = "callId",
                )

                verify(exactly = 1) {
                    altinnClient.sendToVirksomhet(
                        altinnMelding
                    )
                }
                verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any()) }
            }
        }

        it("Send brev to Altinn, and not varsel to nærmeste leder when no oppfolgingstilfelle exists") {
            coEvery { oppfolgingstilfelleClient.oppfolgingstilfellePerson(any(), any(), any()) } returns null
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)
            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                false
            )

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Unbounded) {
                varselService.sendVarsel(
                    varselType = MotedeltakerVarselType.INNKALT,
                    isDigitalVarselEnabledForArbeidstaker = false,
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                    arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                    arbeidstakerbrevId = UUID.randomUUID(),
                    narmesteLeder = null,
                    virksomhetsbrevId = virksomhetsbrevId,
                    virksomhetsPdf = virksomhetsPdf,
                    virksomhetsnummer = VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                    behandlerId = null,
                    behandlerRef = null,
                    behandlerDocument = emptyList(),
                    behandlerPdf = byteArrayOf(0x2E, 0x38),
                    behandlerbrevId = null,
                    behandlerbrevParentId = null,
                    behandlerInnkallingUuid = null,
                    motetidspunkt = LocalDateTime.now().plusDays(1L),
                    token = "token",
                    callId = "callId",
                )

                verify(exactly = 1) {
                    altinnClient.sendToVirksomhet(
                        altinnMelding
                    )
                }
                verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any()) }
            }
        }
    }
})
