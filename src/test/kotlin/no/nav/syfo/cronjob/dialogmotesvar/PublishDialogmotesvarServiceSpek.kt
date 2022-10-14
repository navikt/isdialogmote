package no.nav.syfo.cronjob.dialogmotesvar

import io.mockk.*
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteWithBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

class PublishDialogmotesvarServiceSpek : Spek({
    describe("Publish and update dialogmøtesvar") {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val dialogmotesvarProducer = mockk<DialogmotesvarProducer>()
        val publishDialogmotesvarService = PublishDialogmotesvarService(database, dialogmotesvarProducer)

        beforeEachTest {
            justRun { dialogmotesvarProducer.sendDialogmotesvar(any(), any()) }
        }
        afterEachTest {
            clearMocks(dialogmotesvarProducer)
            database.dropData()
        }

        describe("Publish on kafka") {
            it("Publish møtesvar on kafka topic") {
                clearMocks(dialogmotesvarProducer)
                val dbRef = UUID.randomUUID()
                val moteid = UUID.randomUUID()
                val threeDaysAgo = OffsetDateTime.now().minusDays(3)
                val now = OffsetDateTime.now()
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
                )
                val kDialogmotesvar = KDialogmotesvar(
                    ident = UserConstants.ARBEIDSTAKER_FNR,
                    svarType = DialogmoteSvarType.KOMMER,
                    senderType = SenderType.ARBEIDSTAKER,
                    brevSentAt = threeDaysAgo,
                    svarReceivedAt = now,
                )
                justRun { dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteid) }

                publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

                verify(exactly = 1) { dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteid) }
            }
        }

        describe("Get unpublished dialogmøtesvar ") {
            it("Behandler answers more than once to the same meeting") {
                val identifiers = database.connection.createNewDialogmoteWithReferences(
                    newDialogmote = generateNewDialogmoteWithBehandler(
                        UserConstants.ARBEIDSTAKER_FNR,
                        DialogmoteStatus.INNKALT,
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
                database.connection.updateBehandlersvarPublishedAt(kommerIkkeSvarUuid)
                val kommerLikevelSvarUuid = UUID.randomUUID()
                database.connection.createBehandlerVarselSvar(
                    svarUuid = kommerLikevelSvarUuid,
                    varselId = varselId,
                    svarType = DialogmoteSvarType.KOMMER,
                )

                val dialogmotesvarList = publishDialogmotesvarService.getUnpublishedDialogmotesvar()

                dialogmotesvarList.size shouldBeEqualTo 1
            }

            it("Get unpublished møtesvar from both arbeidsgiver and arbeidstaker") {
                val identifiers = database.connection.createNewDialogmoteWithReferences(
                    newDialogmote = generateNewDialogmoteWithBehandler(
                        UserConstants.ARBEIDSTAKER_FNR,
                        DialogmoteStatus.INNKALT,
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

                dialogmotesvarList.size shouldBeEqualTo 2
            }
        }

        describe("Update database") {
            it("Mark møtesvar from arbeidstaker as published") {
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
                )
                database.connection.createArbeidstakerVarsel(
                    varselUuid = varseluuid,
                    varselType = MotedeltakerVarselType.INNKALT,
                )

                publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

                val varsler = database.getMotedeltakerArbeidstakerVarsel(varseluuid)
                varsler.size shouldBeEqualTo 1
                val varsel = varsler[0]
                varsel.svarPublishedToKafkaAt shouldNotBe null
            }

            it("Mark møtesvar from arbeidsgiver as published") {
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
                )
                database.connection.createArbeidsgiverVarsel(
                    varselUuid = varseluuid,
                    varselType = MotedeltakerVarselType.INNKALT,
                )

                publishDialogmotesvarService.publishAndUpdateDialogmotesvar(dialogmotesvar)

                val varsler = database.getMotedeltakerArbeidsgiverVarsel(varseluuid)
                varsler.size shouldBeEqualTo 1
                val varsel = varsler[0]
                varsel.svarPublishedToKafkaAt shouldNotBe null
            }
        }
    }
})
