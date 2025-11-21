package no.nav.syfo.janitor

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.api.dto.toNewDialogmote
import no.nav.syfo.application.JanitorService
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerService
import no.nav.syfo.infrastructure.database.dialogmote.DialogmoterelasjonService
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotestatusService
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.getDialogmoteList
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.janitor.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

fun generateJanitorEventDTO(action: String, referenceUuid: String): JanitorEventDTO = JanitorEventDTO(
    referenceUUID = referenceUuid,
    navident = UserConstants.VEILEDER_IDENT,
    eventUUID = UUID.randomUUID().toString(),
    personident = UserConstants.ARBEIDSTAKER_FNR.value,
    action = action,
)

val objectMapper: ObjectMapper = configuredJacksonMapper()

class JanitorServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val eventStatusProducerMock = mockk<JanitorEventStatusProducer>(relaxed = true)

    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)
    private val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = mockk(relaxed = true),
        moteStatusEndretRepository = moteStatusEndretRepository,
    )
    private val dialogmotedeltakerService =
        DialogmotedeltakerService(database = database, arbeidstakerVarselService = mockk())
    private val dialogmoterelasjonService = DialogmoterelasjonService(
        database = database,
        dialogmotedeltakerService = dialogmotedeltakerService,
    )

    private val janitorService = JanitorService(
        database = database,
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        janitorEventStatusProducer = eventStatusProducerMock,
    )

    @BeforeEach
    fun beforeEach() {
        justRun { eventStatusProducerMock.sendEventStatus(any()) }
        clearMocks(eventStatusProducerMock)
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Handles lukk møte event")
    inner class HandlesLukkMoteEvent {

        @Test
        fun `updates motestatus to LUKKET and produces event status OK`() {
            val mote = database.createDialogmote()

            val event = generateJanitorEventDTO(
                action = JanitorAction.LUKK_DIALOGMOTE.name,
                referenceUuid = mote.uuid.toString()
            )
            runBlocking { janitorService.handle(event) }

            val motestatusList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
            assertTrue(motestatusList.isNotEmpty())
            val motestatus = motestatusList.last()
            assertEquals(DialogmoteStatus.LUKKET, motestatus.status)
            assertEquals(UserConstants.VEILEDER_IDENT, motestatus.opprettetAv)
            assertEquals(mote.id, motestatus.moteId)

            verify {
                eventStatusProducerMock.sendEventStatus(
                    JanitorEventStatusDTO(
                        eventUUID = event.eventUUID,
                        status = JanitorEventStatus.OK
                    )
                )
            }
        }

        @Test
        fun `produces event status FAILED if mote not found`() {
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

        @Test
        fun `produces event status FAILED if mote finished`() {
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

        @Test
        fun `produces event status FAILED if mote on wrong person`() {
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

    @Nested
    @DisplayName("Handles irrelevant event")
    inner class HandlesIrrelevantEvent {

        @Test
        fun `does not update mote status or produce event status`() {
            runBlocking {
                janitorService.handle(
                    generateJanitorEventDTO(
                        "IRRELEVANT_ACTION",
                        UUID.randomUUID().toString()
                    )
                )
            }

            assertTrue(moteStatusEndretRepository.getMoteStatusEndretNotPublished().isEmpty())
            verify(exactly = 0) { eventStatusProducerMock.sendEventStatus(any()) }
        }
    }
}

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
