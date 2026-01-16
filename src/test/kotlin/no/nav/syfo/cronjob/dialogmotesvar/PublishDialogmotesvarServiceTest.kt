package no.nav.syfo.cronjob.dialogmotesvar

import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.*
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.updateBehandlersvarPublishedAt
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteWithBehandler
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime
import java.util.*

class PublishDialogmotesvarServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val dialogmotesvarProducer = mockk<DialogmotesvarProducer>()
    private val publishDialogmotesvarService = PublishDialogmotesvarService(database, dialogmotesvarProducer)

    @BeforeEach
    fun beforeEach() {
        justRun { dialogmotesvarProducer.sendDialogmotesvar(any(), any()) }
    }

    @AfterEach
    fun afterEach() {
        clearMocks(dialogmotesvarProducer)
        database.dropData()
    }

    @Nested
    @DisplayName("Publish on kafka")
    inner class PublishOnKafka {

        @Test
        fun `Publish møtesvar on kafka topic`() {
            clearMocks(dialogmotesvarProducer)
            val dbRef = UUID.randomUUID()
            val moteid = UUID.randomUUID()
            val threeDaysAgo = OffsetDateTime.now().minusDays(3)
            val now = OffsetDateTime.now()
            val svarTekst = "Jeg kommer til møtet"
            database.connection.createArbeidstakerVarsel(
                varselUuid = dbRef,
                varselType = MotedeltakerVarselType.INNKALT,
            )
            val dialogmotesvar = Dialogmotesvar(
                moteuuid = moteid,
                dbRef = dbRef,
                ident = UserConstants.ARBEIDSTAKER_FNR,
                svarType = DialogmoteSvarType.KOMMER,
                senderType = SenderType.ARBEIDSTAKER,
                brevSentAt = threeDaysAgo,
                svarReceivedAt = now,
                svarTekst = svarTekst,
            )
            val kDialogmotesvar = KDialogmotesvar(
                ident = UserConstants.ARBEIDSTAKER_FNR,
                svarType = DialogmoteSvarType.KOMMER,
                senderType = SenderType.ARBEIDSTAKER,
                brevSentAt = threeDaysAgo,
                svarReceivedAt = now,
                svarTekst = svarTekst,
            )
            justRun { dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteid) }

            publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

            verify(exactly = 1) { dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteid) }
        }
    }

    @Nested
    @DisplayName("Get unpublished dialogmøtesvar")
    inner class GetUnpublished {

        @Test
        fun `Behandler answers more than once to the same meeting`() {
            val identifiers = database.connection.createNewDialogmoteWithReferences(
                newDialogmote = generateNewDialogmoteWithBehandler(
                    UserConstants.ARBEIDSTAKER_FNR,
                    Dialogmote.Status.INNKALT,
                )
            )
            val varselId = database.connection.createBehandlerVarsel(
                UUID.randomUUID(),
                MotedeltakerVarselType.INNKALT,
                identifiers.motedeltakerBehandlerIdPair?.first,
            )
            val kommerIkkeSvarUuid = UUID.randomUUID()
            database.connection.createBehandlerVarselSvar(
                svarUuid = kommerIkkeSvarUuid,
                varselId = varselId,
                svarType = DialogmoteSvarType.KOMMER_IKKE,
            )
            database.updateBehandlersvarPublishedAt(kommerIkkeSvarUuid)
            val kommerLikevelSvarUuid = UUID.randomUUID()
            database.connection.createBehandlerVarselSvar(
                svarUuid = kommerLikevelSvarUuid,
                varselId = varselId,
                svarType = DialogmoteSvarType.KOMMER,
            )

            val dialogmotesvarList = publishDialogmotesvarService.getUnpublishedDialogmotesvar()

            assertEquals(1, dialogmotesvarList.size)
        }

        @Test
        fun `Behandlers answer is too late, but the answer is still published`() {
            val identifiers = database.connection.createNewDialogmoteWithReferences(
                newDialogmote = generateNewDialogmoteWithBehandler(
                    UserConstants.ARBEIDSTAKER_FNR,
                    Dialogmote.Status.AVLYST,
                )
            )
            val varselId = database.connection.createBehandlerVarsel(
                UUID.randomUUID(),
                MotedeltakerVarselType.INNKALT,
                identifiers.motedeltakerBehandlerIdPair?.first,
            )
            val kommerIkkeSvarUuid = UUID.randomUUID()
            database.connection.createBehandlerVarselSvar(
                svarUuid = kommerIkkeSvarUuid,
                varselId = varselId,
                svarType = DialogmoteSvarType.KOMMER_IKKE,
            )

            val dialogmotesvarList = publishDialogmotesvarService.getUnpublishedDialogmotesvar()

            assertEquals(1, dialogmotesvarList.size)
        }

        @Test
        fun `Get unpublished møtesvar from both arbeidsgiver and arbeidstaker`() {
            val identifiers = database.connection.createNewDialogmoteWithReferences(
                newDialogmote = generateNewDialogmoteWithBehandler(
                    UserConstants.ARBEIDSTAKER_FNR,
                    Dialogmote.Status.INNKALT,
                )
            )
            database.connection.createArbeidsgiverVarsel(
                varselUuid = UUID.randomUUID(),
                varselType = MotedeltakerVarselType.INNKALT,
                motedeltakerArbeidsgiverId = identifiers.motedeltakerArbeidsgiverIdPair.first,
                harSvart = true,
            )
            database.connection.createArbeidstakerVarsel(
                varselUuid = UUID.randomUUID(),
                varselType = MotedeltakerVarselType.INNKALT,
                motedeltakerArbeidstakerId = identifiers.motedeltakerArbeidstakerIdPair.first,
                harSvart = true,
            )

            val dialogmotesvarList = publishDialogmotesvarService.getUnpublishedDialogmotesvar()

            assertEquals(2, dialogmotesvarList.size)
        }
    }

    @Nested
    @DisplayName("Update database")
    inner class UpdateDatabase {

        @Test
        fun `Mark møtesvar from arbeidstaker as published`() {
            val moteid = UUID.randomUUID()
            val varseluuid = UUID.randomUUID()
            val threeDaysAgo = OffsetDateTime.now().minusDays(3)
            val now = OffsetDateTime.now()
            val dialogmotesvar = Dialogmotesvar(
                moteuuid = moteid,
                dbRef = varseluuid,
                ident = UserConstants.ARBEIDSTAKER_FNR,
                svarType = DialogmoteSvarType.KOMMER,
                senderType = SenderType.ARBEIDSTAKER,
                brevSentAt = threeDaysAgo,
                svarReceivedAt = now,
                svarTekst = null,
            )
            database.connection.createArbeidstakerVarsel(
                varselUuid = varseluuid,
                varselType = MotedeltakerVarselType.INNKALT,
            )

            publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

            val varsler = database.getMotedeltakerArbeidstakerVarsel(varseluuid)
            assertEquals(1, varsler.size)
            val varsel = varsler[0]
            assertNotNull(varsel.svarPublishedToKafkaAt)
        }

        @Test
        fun `Mark møtesvar from arbeidsgiver as published`() {
            val moteid = UUID.randomUUID()
            val varseluuid = UUID.randomUUID()
            val threeDaysAgo = OffsetDateTime.now().minusDays(3)
            val now = OffsetDateTime.now()
            val dialogmotesvar = Dialogmotesvar(
                moteuuid = moteid,
                dbRef = varseluuid,
                ident = UserConstants.ARBEIDSTAKER_FNR,
                svarType = DialogmoteSvarType.KOMMER,
                senderType = SenderType.ARBEIDSGIVER,
                brevSentAt = threeDaysAgo,
                svarReceivedAt = now,
                svarTekst = null,
            )
            database.connection.createArbeidsgiverVarsel(
                varselUuid = varseluuid,
                varselType = MotedeltakerVarselType.INNKALT,
            )

            publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

            val varsler = database.getMotedeltakerArbeidsgiverVarsel(varseluuid)
            assertEquals(1, varsler.size)
            val varsel = varsler[0]
            assertNotNull(varsel.svarPublishedToKafkaAt)
        }
    }
}
