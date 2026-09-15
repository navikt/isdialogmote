package no.nav.syfo.brev.behandler

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.database.Database
import no.nav.syfo.infrastructure.database.createMotedeltakerBehandlerVarselSvar
import no.nav.syfo.infrastructure.database.getMote
import no.nav.syfo.infrastructure.database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateDialogmoteSvar
import no.nav.syfo.testhelper.generator.generateDocumentComponentList
import no.nav.syfo.testhelper.generator.generatePDialogmote
import no.nav.syfo.testhelper.generator.generatePMotedeltakerBehandlerVarsel
import no.nav.syfo.testhelper.mock.mockBehandlerDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class BehandlerVarselServiceTest {
    private val database: Database = mockk()
    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val dialogmeldingClient = mockk<DialogmeldingClient>(relaxed = true)
    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        dialogmeldingClient = dialogmeldingClient,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )

    @BeforeEach
    fun beforeEach() {
        mockkStatic("no.nav.syfo.infrastructure.database.MoteQueryKt")
        mockkStatic("no.nav.syfo.infrastructure.database.MotedeltakerBehandlerVarselQueryKt")
        mockkStatic("no.nav.syfo.infrastructure.database.MotedeltakerBehandlerVarselSvarQueryKt")
    }

    @AfterEach
    fun afterEach() {
        clearMocks(database)
        clearMocks(behandlerDialogmeldingProducer)
        clearMocks(dialogmeldingClient)
        unmockkStatic("no.nav.syfo.infrastructure.database.MoteQueryKt")
        unmockkStatic("no.nav.syfo.infrastructure.database.MotedeltakerBehandlerVarselQueryKt")
        unmockkStatic("no.nav.syfo.infrastructure.database.MotedeltakerBehandlerVarselSvarQueryKt")
    }

    @Test
    fun `return false and don't store anything if no associated meeting is found`() {
        val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
        val conversationRef = "123456789"
        val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
        val dialogmeldingSvar = generateDialogmoteSvar()

        every {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                any(),
                any(),
                any()
            )
        } returns Pair(1, pMotedeltakerBehandlerVarsel)
        every { database.getMote(any()) } returns null

        val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
            dialogmeldingSvar = dialogmeldingSvar,
            msgId = "321",
        )

        assertFalse(isSvarSaved)
        verify(exactly = 1) {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                varselType = varseltypeInnkalt,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                uuid = conversationRef
            )
        }
        verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
        verify(exactly = 0) {
            database.createMotedeltakerBehandlerVarselSvar(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `persist motesvar as valid when mote is unfinished`() {
        val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
        val conversationRef = "123456789"
        val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
        val dialogmeldingSvar = generateDialogmoteSvar()
        val pDialogmote = generatePDialogmote()
        every {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                any(),
                any(),
                any()
            )
        } returns Pair(1, pMotedeltakerBehandlerVarsel)
        every { database.getMote(any()) } returns pDialogmote
        every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(
            1,
            UUID.randomUUID()
        )

        val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
            dialogmeldingSvar = dialogmeldingSvar,
            msgId = "321",
        )

        assertTrue(isSvarSaved)
        verify(exactly = 1) {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                varselType = varseltypeInnkalt,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                uuid = conversationRef
            )
        }
        verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
        verify(exactly = 1) {
            database.createMotedeltakerBehandlerVarselSvar(
                motedeltakerBehandlerVarselId = 1,
                type = DialogmoteSvarType.KOMMER_IKKE,
                tekst = "tekst",
                msgId = "321"
            )
        }
    }

    @Test
    fun `persist motesvar as valid when it was sent before mote was finished`() {
        val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
        val conversationRef = "123456789"
        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
        val dialogmeldingSvar = generateDialogmoteSvar().copy(
            opprettetTidspunkt = twoDaysAgo,
        )
        val pDialogmote = generatePDialogmote().copy(
            status = Dialogmote.Status.FERDIGSTILT.name,
            updatedAt = LocalDateTime.now()
        )
        every {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                any(),
                any(),
                any()
            )
        } returns Pair(1, pMotedeltakerBehandlerVarsel)
        every { database.getMote(any()) } returns pDialogmote
        every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(
            1,
            UUID.randomUUID()
        )

        val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
            dialogmeldingSvar = dialogmeldingSvar,
            msgId = "321",
        )

        assertTrue(isSvarSaved)
        verify(exactly = 1) {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                varselType = varseltypeInnkalt,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                uuid = conversationRef
            )
        }
        verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
        verify(exactly = 1) {
            database.createMotedeltakerBehandlerVarselSvar(
                motedeltakerBehandlerVarselId = 1,
                type = DialogmoteSvarType.KOMMER_IKKE,
                tekst = "tekst",
                msgId = "321"
            )
        }
    }

    @Test
    fun `persist motesvar as invalid when it was sent after mote was finished`() {
        val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
        val conversationRef = "123456789"
        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
        val dialogmeldingSvar = generateDialogmoteSvar().copy(
            opprettetTidspunkt = LocalDateTime.now(),
        )
        val pDialogmote = generatePDialogmote().copy(
            status = Dialogmote.Status.FERDIGSTILT.name,
            updatedAt = twoDaysAgo,
        )
        every {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                any(),
                any(),
                any()
            )
        } returns Pair(1, pMotedeltakerBehandlerVarsel)
        every { database.getMote(any()) } returns pDialogmote
        every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(
            1,
            UUID.randomUUID()
        )

        val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
            dialogmeldingSvar = dialogmeldingSvar,
            msgId = "321",
        )

        assertTrue(isSvarSaved)
        verify(exactly = 1) {
            database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                varselType = varseltypeInnkalt,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                uuid = conversationRef
            )
        }
        verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
        verify(exactly = 1) {
            database.createMotedeltakerBehandlerVarselSvar(
                motedeltakerBehandlerVarselId = 1,
                type = DialogmoteSvarType.KOMMER_IKKE,
                tekst = "tekst",
                msgId = "321"
            )
        }
    }

    @Test
    fun `send varsel to original behandler when behandler can receive dialogmelding`() {
        coEvery { dialogmeldingClient.getBehandler(any()) } returns mockBehandlerDTO
        val dialogmeldingSlot = slot<KafkaBehandlerDialogmeldingDTO>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(capture(dialogmeldingSlot)) }

        runBlocking {
            behandlerVarselService.sendVarsel(
                behandlerRef = UserConstants.BEHANDLER_REF,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                document = generateDocumentComponentList(),
                pdf = byteArrayOf(),
                varseltype = MotedeltakerVarselType.INNKALT,
                varselUuid = UUID.randomUUID(),
                varselParentId = null,
                varselInnkallingUuid = null,
                token = "token",
                callId = "callId",
            )
        }

        assertEquals(UserConstants.BEHANDLER_REF, dialogmeldingSlot.captured.behandlerRef)
        coVerify(exactly = 0) { dialogmeldingClient.getBehandlereForPerson(any(), any(), any()) }
    }

    @Test
    fun `send varsel to erstatningsbehandler when original behandler is invalidated`() {
        val invalidatedBehandlerDTO = mockBehandlerDTO.copy(invalidated = true)
        val erstatningsbehandlerRef = UUID.randomUUID().toString()
        val erstatningsbehandlerDTO = mockBehandlerDTO.copy(
            behandlerRef = erstatningsbehandlerRef,
            type = "FASTLEGE",
            kontorDialogmeldingmeldingEnabled = true,
        )
        coEvery { dialogmeldingClient.getBehandler(any()) } returns invalidatedBehandlerDTO
        coEvery {
            dialogmeldingClient.getBehandlereForPerson(any(), any(), any())
        } returns listOf(erstatningsbehandlerDTO)
        val dialogmeldingSlot = slot<KafkaBehandlerDialogmeldingDTO>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(capture(dialogmeldingSlot)) }

        runBlocking {
            behandlerVarselService.sendVarsel(
                behandlerRef = UserConstants.BEHANDLER_REF,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                document = generateDocumentComponentList(),
                pdf = byteArrayOf(),
                varseltype = MotedeltakerVarselType.INNKALT,
                varselUuid = UUID.randomUUID(),
                varselParentId = null,
                varselInnkallingUuid = null,
                token = "token",
                callId = "callId",
            )
        }

        assertEquals(erstatningsbehandlerRef, dialogmeldingSlot.captured.behandlerRef)
    }

    @Test
    fun `send varsel to original behandler when no erstatningsbehandler is found`() {
        val invalidatedBehandlerDTO = mockBehandlerDTO.copy(invalidated = true)
        coEvery { dialogmeldingClient.getBehandler(any()) } returns invalidatedBehandlerDTO
        coEvery {
            dialogmeldingClient.getBehandlereForPerson(any(), any(), any())
        } returns emptyList()
        val dialogmeldingSlot = slot<KafkaBehandlerDialogmeldingDTO>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(capture(dialogmeldingSlot)) }

        runBlocking {
            behandlerVarselService.sendVarsel(
                behandlerRef = UserConstants.BEHANDLER_REF,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                document = generateDocumentComponentList(),
                pdf = byteArrayOf(),
                varseltype = MotedeltakerVarselType.INNKALT,
                varselUuid = UUID.randomUUID(),
                varselParentId = null,
                varselInnkallingUuid = null,
                token = "token",
                callId = "callId",
            )
        }

        assertEquals(UserConstants.BEHANDLER_REF, dialogmeldingSlot.captured.behandlerRef)
    }

    @Test
    fun `throw exception when behandler is not found`() {
        coEvery { dialogmeldingClient.getBehandler(any()) } returns null

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                behandlerVarselService.sendVarsel(
                    behandlerRef = UserConstants.BEHANDLER_REF,
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                    document = generateDocumentComponentList(),
                    pdf = byteArrayOf(),
                    varseltype = MotedeltakerVarselType.INNKALT,
                    varselUuid = UUID.randomUUID(),
                    varselParentId = null,
                    varselInnkallingUuid = null,
                    token = "token",
                    callId = "callId",
                )
            }
        }

        verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
    }
}
