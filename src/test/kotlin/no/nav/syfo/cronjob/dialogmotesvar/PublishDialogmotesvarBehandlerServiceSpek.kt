package no.nav.syfo.cronjob.dialogmotesvar

import io.mockk.*
import no.nav.syfo.dialogmote.database.getMotedeltakerBehandlerVarselSvar
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

class PublishDialogmotesvarBehandlerServiceSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    val database = externalMockEnvironment.database
    val dialogmotesvarProducer = mockk<DialogmotesvarProducer>()
    val publishDialogmotesvarService = PublishDialogmotesvarService(database, dialogmotesvarProducer)

    describe("Publish møtesvar") {
        beforeEachTest {
            justRun { dialogmotesvarProducer.sendDialogmotesvar(any(), any()) }
        }
        afterEachTest {
            clearMocks(dialogmotesvarProducer)
            database.dropData()
        }

        describe("Mark møtesvar from behandler as published in the database") {
            it("Mark as published when only one response") {
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

                val varsler = database.getMotedeltakerBehandlerVarselSvar(varselId)
                varsler.size shouldBeEqualTo 1
                val varsel = varsler[0]
                varsel.svarPublishedToKafkaAt shouldNotBe null
            }
        }
    }
})
