package no.nav.syfo.dialogmote

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.NarmesteLederVarselService
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.altinn.AltinnClient
import no.nav.syfo.infrastructure.client.altinn.createAltinnMelding
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.ARBEIDSGIVERPERIODE_DAYS
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.database.dialogmote.VarselService
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_NO_NARMESTELEDER
import no.nav.syfo.testhelper.generator.DIALOGMOTE_TIDSPUNKT_FIXTURE
import no.nav.syfo.testhelper.mock.narmesteLeder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenClient

class VarselServiceTest {

    private val arbeidstakerVarselService = mockk<ArbeidstakerVarselService>()
    private val narmesteLederVarselService = mockk<NarmesteLederVarselService>()
    private val behandlerVarselService = mockk<BehandlerVarselService>()
    private val altinnClient = mockk<AltinnClient>()
    private val oppfolgingstilfelleClient = mockk<OppfolgingstilfelleClient>()
    private val anyOppfolgingstilfelle = Oppfolgingstilfelle(
        start = LocalDate.now().minusDays(10),
        end = LocalDate.now().plusDays(10),
    )

    private val varselService = VarselService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        narmesteLederVarselService = narmesteLederVarselService,
        behandlerVarselService = behandlerVarselService,
        altinnClient = altinnClient,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        isAltinnSendingEnabled = true,
        dokumentportenClient = mockk<DokumentportenClient>(relaxed = true),
        isDokumentportenSendingEnabled = true,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(arbeidstakerVarselService)
        clearMocks(narmesteLederVarselService)
        clearMocks(behandlerVarselService)
        clearMocks(altinnClient)
        clearMocks(oppfolgingstilfelleClient)

        justRun { arbeidstakerVarselService.sendVarsel(any(), any(), any(), any(), any()) }
        justRun { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        justRun { behandlerVarselService.sendVarsel(any(), any(), any(), any(), any(), any(), any(), any()) }
        justRun { altinnClient.sendToVirksomhet(any()) }
    }

    @Test
    fun `Send varsel to nærmeste leder`() {
        coEvery {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                any(),
                any(),
                any()
            )
        } returns anyOppfolgingstilfelle
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

        runBlocking {
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
                motetidspunkt = DIALOGMOTE_TIDSPUNKT_FIXTURE,
                token = "token",
                callId = "callId",
            )

            verify(exactly = 1) { altinnClient.sendToVirksomhet(altinnMelding) }
            verify(exactly = 1) {
                narmesteLederVarselService.sendVarsel(
                    narmesteLeder,
                    MotedeltakerVarselType.INNKALT,
                    DIALOGMOTE_TIDSPUNKT_FIXTURE
                )
            }
        }
    }

    @Test
    fun `Send brev to Altinn when no nærmeste leder`() {
        coEvery {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                any(),
                any(),
                any()
            )
        } returns anyOppfolgingstilfelle
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

        runBlocking {
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
            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        }
    }

    @Test
    fun `Send brev to Altinn, and not varsel to nærmeste leder when no active tilfelle`() {
        coEvery {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                any(),
                any(),
                any()
            )
        } returns Oppfolgingstilfelle(
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

        runBlocking {
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
            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        }
    }

    @Test
    fun `Send brev to Altinn, and not varsel to nærmeste leder when no oppfolgingstilfelle exists`() {
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

        runBlocking {
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
            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        }
    }
}
