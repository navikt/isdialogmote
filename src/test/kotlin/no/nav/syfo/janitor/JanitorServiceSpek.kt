package no.nav.syfo.janitor

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerService
import no.nav.syfo.infrastructure.database.dialogmote.DialogmoterelasjonService
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotestatusService
import no.nav.syfo.api.dto.toNewDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.application.JanitorService
import no.nav.syfo.infrastructure.kafka.janitor.JanitorAction
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventDTO
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatus
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusDTO
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.infrastructure.database.dialogmote.database.getDialogmoteList
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

fun generateJanitorEventDTO(action: String, referenceUuid: String): JanitorEventDTO = JanitorEventDTO(
    referenceUUID = referenceUuid,
    navident = UserConstants.VEILEDER_IDENT,
    eventUUID = UUID.randomUUID().toString(),
    personident = UserConstants.ARBEIDSTAKER_FNR.value,
    action = action,
)

val objectMapper: ObjectMapper = configuredJacksonMapper()

class JanitorServiceSpek : Spek({

    describe(JanitorService::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val eventStatusProducerMock = mockk<JanitorEventStatusProducer>(relaxed = true)
        justRun { eventStatusProducerMock.sendEventStatus(any()) }

        val moteStatusEndretRepository = MoteStatusEndretRepository(database)
        val dialogmotestatusService = DialogmotestatusService(
            oppfolgingstilfelleClient = mockk(relaxed = true),
            moteStatusEndretRepository = moteStatusEndretRepository,
        )
        val dialogmotedeltakerService =
            DialogmotedeltakerService(database = database, arbeidstakerVarselService = mockk())
        val dialogmoterelasjonService = DialogmoterelasjonService(
            database = database,
            dialogmotedeltakerService = dialogmotedeltakerService,
        )

        val janitorService = JanitorService(
            database = database,
            dialogmotestatusService = dialogmotestatusService,
            dialogmoterelasjonService = dialogmoterelasjonService,
            janitorEventStatusProducer = eventStatusProducerMock,
        )

        beforeEachTest {
            clearMocks(eventStatusProducerMock)
        }

        afterEachTest {
            database.dropData()
        }

        describe("Handles lukk møte event") {
            it("updates motestatus to LUKKET and produces event status OK") {
                val mote = database.createDialogmote()

                val event = generateJanitorEventDTO(
                    action = JanitorAction.LUKK_DIALOGMOTE.name,
                    referenceUuid = mote.uuid.toString()
                )
                runBlocking { janitorService.handle(event) }

                val motestatusList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                motestatusList.shouldNotBeEmpty()
                val motestatus = motestatusList.last()
                motestatus.status shouldBeEqualTo DialogmoteStatus.LUKKET
                motestatus.opprettetAv shouldBeEqualTo UserConstants.VEILEDER_IDENT
                motestatus.moteId shouldBeEqualTo mote.id

                verify {
                    eventStatusProducerMock.sendEventStatus(
                        JanitorEventStatusDTO(
                            eventUUID = event.eventUUID,
                            status = JanitorEventStatus.OK
                        )
                    )
                }
            }

            it("produces event status FAILED if mote not found") {
                val event = generateJanitorEventDTO(
                    action = JanitorAction.LUKK_DIALOGMOTE.name,
                    referenceUuid = UUID.randomUUID().toString()
                )
                runBlocking { janitorService.handle(event) }

                verify {
                    eventStatusProducerMock.sendEventStatus(
                        JanitorEventStatusDTO(
                            eventUUID = event.eventUUID,
                            status = JanitorEventStatus.FAILED
                        )
                    )
                }
            }

            it("produces event status FAILED if mote finished") {
                val mote = database.createDialogmote()
                val moteUuid = mote.uuid
                database.updateMoteStatus(moteUUID = moteUuid, newMoteStatus = DialogmoteStatus.FERDIGSTILT)

                val event = generateJanitorEventDTO(
                    action = JanitorAction.LUKK_DIALOGMOTE.name,
                    referenceUuid = moteUuid.toString()
                )

                runBlocking { janitorService.handle(event) }

                verify {
                    eventStatusProducerMock.sendEventStatus(
                        JanitorEventStatusDTO(
                            eventUUID = event.eventUUID,
                            status = JanitorEventStatus.FAILED
                        )
                    )
                }
            }

            it("produces event status FAILED if mote on wrong person") {
                val moteUuid = database.createDialogmote()

                val event = generateJanitorEventDTO(
                    action = JanitorAction.LUKK_DIALOGMOTE.name,
                    referenceUuid = moteUuid.toString()
                ).copy(personident = UserConstants.ARBEIDSTAKER_ANNEN_FNR.value)

                runBlocking { janitorService.handle(event) }

                verify {
                    eventStatusProducerMock.sendEventStatus(
                        JanitorEventStatusDTO(
                            eventUUID = event.eventUUID,
                            status = JanitorEventStatus.FAILED
                        )
                    )
                }
            }
        }

        describe("Handles irrelevant event") {
            it("does not update mote status or produce event status") {
                runBlocking {
                    janitorService.handle(
                        generateJanitorEventDTO(
                            "IRRELEVANT_ACTION",
                            UUID.randomUUID().toString()
                        )
                    )
                }

                moteStatusEndretRepository.getMoteStatusEndretNotPublished().shouldBeEmpty()
                verify(exactly = 0) { eventStatusProducerMock.sendEventStatus(any()) }
            }
        }
    }
})

private fun TestDatabase.createDialogmote(): PDialogmote {
    val newDialogmoteDTO = generateNewDialogmoteDTO(
        personIdent = UserConstants.ARBEIDSTAKER_FNR,
    )
    this.connection.use { connection ->
        connection.createNewDialogmoteWithReferences(
            newDialogmote = newDialogmoteDTO.toNewDialogmote(
                requestByNAVIdent = UserConstants.VEILEDER_IDENT,
                navEnhet = UserConstants.ENHET_NR
            ),
        )
    }

    return this.getDialogmoteList(UserConstants.ARBEIDSTAKER_FNR).first()
}
