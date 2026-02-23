package no.nav.syfo.cronjob.dialogmotesvar

import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.Dialogmotesvar
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.DialogmotesvarProducer
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.PublishDialogmotesvarService
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.SenderType
import no.nav.syfo.testhelper.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.time.OffsetDateTime
import java.util.*

class PublishDialogmotesvarBehandlerServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val dialogmotesvarProducer = mockk<DialogmotesvarProducer>()
    private val publishDialogmotesvarService = PublishDialogmotesvarService(database, dialogmotesvarProducer)
    private val moteRepository = externalMockEnvironment.moteRepository

    @BeforeEach
    fun beforeEach() {
        justRun { dialogmotesvarProducer.sendDialogmotesvar(any(), any()) }
    }

    @AfterEach
    fun afterEach() {
        clearMocks(dialogmotesvarProducer)
        database.dropData()
    }

    @Test
    fun `Mark as published when only one response`() {
        val moteid = UUID.randomUUID()
        val svaruuid = UUID.randomUUID()
        val threeDaysAgo = OffsetDateTime.now().minusDays(3)
        val now = OffsetDateTime.now()
        val dialogmotesvar = Dialogmotesvar(
            moteuuid = moteid,
            dbRef = svaruuid,
            ident = UserConstants.ARBEIDSTAKER_FNR,
            svarType = DialogmoteSvarType.KOMMER,
            senderType = SenderType.BEHANDLER,
            brevSentAt = threeDaysAgo,
            svarReceivedAt = now,
            svarTekst = null,
        )
        val varselId = database.connection.createBehandlerVarsel(
            varseluuid = UUID.randomUUID(),
            varselType = MotedeltakerVarselType.INNKALT,
            motedeltakerBehandlerId = null,
        )
        database.connection.createBehandlerVarselSvar(
            svarUuid = svaruuid,
            varselId = varselId,
            svarType = DialogmoteSvarType.KOMMER
        )

        publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

        val behandlerVarselSvar = database.connection.use {
            with(moteRepository) {
                it.getMoteDeltakerBehandlerVarselSvar(varselId)
            }
        }
        assertEquals(1, behandlerVarselSvar.size)
        val varselSvar = behandlerVarselSvar[0]
        assertNotNull(varselSvar.svarPublishedToKafkaAt)
    }
}
