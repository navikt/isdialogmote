package no.nav.syfo.identhendelse

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateKafkaIdenthendelseDTOGenerator
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class IdenthendelseServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val cacheMock = mockk<ValkeyStore>()
    private val pdlClient = PdlClient(
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        pdlClientId = externalMockEnvironment.environment.pdlClientId,
        pdlUrl = externalMockEnvironment.environment.pdlUrl,
        valkeyStore = cacheMock,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    private val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {

        @Test
        fun `Skal oppdatere database når arbeidstaker har fått ny ident`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            // Populate database with new dialogmote using old ident for arbeidstaker
            val newDialogmote = generateNewDialogmote(personIdent = oldIdent)
            database.connection.use { connection ->
                connection.createNewDialogmoteWithReferences(
                    newDialogmote = newDialogmote,
                    commit = true,
                )
            }

            // Check that arbeidstaker with old/current personident exist in db before update
            val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
            assertEquals(1, currentMotedeltakerArbeidstaker.size)
            val initialUpdatedAt = currentMotedeltakerArbeidstaker.first().updatedAt

            // Check that arbeidstaker with new personident do not exist in db before update
            val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(0, newMotedeltakerArbeidstaker.size)

            runBlocking {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }

            // Check that arbeidstaker with new personident exist in db after update
            val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(1, updatedMotedeltakerArbeidstaker.size)
            assertTrue(updatedMotedeltakerArbeidstaker.first().updatedAt.isAfter(initialUpdatedAt))

            // Check that arbeidstaker with old personident do not exist in db after update
            val oldMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
            assertEquals(0, oldMotedeltakerArbeidstaker.size)
        }

        @Test
        fun `Skal ikke oppdatere database når arbeidstaker ikke finnes i databasen`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = PersonIdent("12333378910")

            // Check that arbeidstaker with old/current personident do not exist in db before update
            val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
            assertEquals(0, currentMotedeltakerArbeidstaker.size)

            // Check that arbeidstaker with new personident do not exist in db before update
            val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(0, newMotedeltakerArbeidstaker.size)

            runBlocking {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }

            // Check that arbeidstaker with new personident still do not exist in db after update
            val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(0, updatedMotedeltakerArbeidstaker.size)
        }

        @Test
        fun `Skal ikke oppdatere database når arbeidstaker ikke har gamle identer`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = false)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!

            // Check that arbeidstaker with new personident do not exist in db before update
            val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(0, newMotedeltakerArbeidstaker.size)

            runBlocking {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }

            // Check that arbeidstaker with new personident still do not exist in db after update
            val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
            assertEquals(0, updatedMotedeltakerArbeidstaker.size)
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {

        @Test
        fun `Skal kaste feil hvis PDL ikke har oppdatert identen`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(
                personident = UserConstants.ARBEIDSTAKER_IKKE_AKTIVT_FNR,
                hasOldPersonident = true,
            )
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            // Populate database with new dialogmote using old ident for arbeidstaker
            val newDialogmote = generateNewDialogmote(personIdent = oldIdent)
            database.connection.use { connection ->
                connection.createNewDialogmoteWithReferences(
                    newDialogmote = newDialogmote,
                    commit = true,
                )
            }

            // Check that arbeidstaker with old/current personident exist in db before update
            val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
            assertEquals(1, currentMotedeltakerArbeidstaker.size)

            runBlocking {
                assertThrows<IllegalStateException> {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }
            }
        }

        @Test
        fun `Skal kaste RuntimeException hvis PDL gir en not_found ved henting av identer`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(
                personident = PersonIdent(UserConstants.ARBEIDSTAKER_WITH_ERROR_FNR.value),
                hasOldPersonident = true,
            )
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            val newDialogmote = generateNewDialogmote(personIdent = oldIdent)
            database.connection.use { connection ->
                connection.createNewDialogmoteWithReferences(
                    newDialogmote = newDialogmote,
                    commit = true,
                )
            }

            val oldMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
            assertEquals(1, oldMotedeltakerArbeidstaker.size)

            runBlocking {
                assertThrows<RuntimeException> {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }
            }
        }
    }
}
