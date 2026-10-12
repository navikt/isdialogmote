package no.nav.syfo.dialogmote

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.NarmesteLederVarselService
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.application.VarselService
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
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenDocumentRequestDTO
import no.nav.syfo.infrastructure.client.ereg.EregClient
import no.nav.syfo.infrastructure.client.ereg.EregVirksomhetsnavn
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertNotNull

class VarselServiceTest {

    private val arbeidstakerVarselService = mockk<ArbeidstakerVarselService>()
    private val narmesteLederVarselService = mockk<NarmesteLederVarselService>()
    private val behandlerVarselService = mockk<BehandlerVarselService>()
    private val oppfolgingstilfelleClient = mockk<OppfolgingstilfelleClient>()
    private val eregClient = mockk<EregClient>()
    private val dokumentportenClient = mockk<DokumentportenClient>(relaxed = true)
    private val anyOppfolgingstilfelle = Oppfolgingstilfelle(
        start = LocalDate.now().minusDays(10),
        end = LocalDate.now().plusDays(10),
    )

    private val varselService = VarselService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        narmesteLederVarselService = narmesteLederVarselService,
        behandlerVarselService = behandlerVarselService,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        dokumentportenClient = dokumentportenClient,
        eregClient = eregClient,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(arbeidstakerVarselService)
        clearMocks(narmesteLederVarselService)
        clearMocks(behandlerVarselService)
        clearMocks(oppfolgingstilfelleClient)
        clearMocks(eregClient)
        clearMocks(dokumentportenClient)

        justRun { arbeidstakerVarselService.sendVarsel(any(), any(), any(), any(), any()) }
        justRun { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        coJustRun { behandlerVarselService.sendVarsel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coEvery { eregClient.organisasjonVirksomhetsnavn(any()) } returns null
    }

    @Test
    fun `Send varsel to nærmeste leder, and send to Dokumentporten`() {
        coEvery {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                any(),
                any(),
                any()
            )
        } returns anyOppfolgingstilfelle
        val virksomhetsbrevId = UUID.randomUUID()
        val virksomhetsPdf = byteArrayOf(0x2E, 0x38)

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

            val documentSlot = slot<DokumentportenDocumentRequestDTO>()
            coVerify(exactly = 1) { dokumentportenClient.sendDocument(capture(documentSlot), "token", "callId") }
            val document = documentSlot.captured
            assertEquals(virksomhetsbrevId, document.documentId)
            assertEquals(VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value, document.orgNumber)
            assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, document.fnr)
            assertArrayEquals(virksomhetsPdf, document.content)
            assertNull(document.varselInstruks)
            coVerify(exactly = 0) { eregClient.organisasjonVirksomhetsnavn(any()) }
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
    fun `send to Dokumentporten with varselInstruks and don't send varsel to nærmeste leder when no nærmeste leder`() {
        coEvery {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                any(),
                any(),
                any()
            )
        } returns anyOppfolgingstilfelle
        coEvery { eregClient.organisasjonVirksomhetsnavn(VIRKSOMHETSNUMMER_NO_NARMESTELEDER) } returns
            EregVirksomhetsnavn(virksomhetsnavn = "Virksomhet AS")
        val virksomhetsbrevId = UUID.randomUUID()
        val virksomhetsPdf = byteArrayOf(0x2E, 0x38)

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

            val documentSlot = slot<DokumentportenDocumentRequestDTO>()
            coVerify(exactly = 1) { dokumentportenClient.sendDocument(capture(documentSlot), "token", "callId") }
            val document = documentSlot.captured
            assertEquals(virksomhetsbrevId, document.documentId)
            assertEquals(VIRKSOMHETSNUMMER_NO_NARMESTELEDER.value, document.orgNumber)
            val varselInstruks = assertNotNull(document.varselInstruks)
            assertTrue(varselInstruks.notifikasjonInnhold.epostTittel.contains("Virksomhet AS"))
            coVerify(exactly = 1) { eregClient.organisasjonVirksomhetsnavn(VIRKSOMHETSNUMMER_NO_NARMESTELEDER) }
            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        }
    }

    @Test
    fun `No varsel to nærmeste leder when no oppfolgingstilfelle exists`() {
        coEvery { oppfolgingstilfelleClient.oppfolgingstilfellePerson(any(), any(), any()) } returns null
        val virksomhetsbrevId = UUID.randomUUID()
        val virksomhetsPdf = byteArrayOf(0x2E, 0x38)

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

            coVerify(exactly = 1) { dokumentportenClient.sendDocument(any(), any(), any()) }
            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any(), any()) }
        }
    }
}
