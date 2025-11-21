package no.nav.syfo.dialogmote.database.repository

import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*

class MoteRepositoryTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private lateinit var moteRepository: MoteRepository
    private lateinit var newDialogmote: no.nav.syfo.domain.dialogmote.NewDialogmote
    private lateinit var newDialogmoteNotBelongingToArbeidstaker: no.nav.syfo.domain.dialogmote.NewDialogmote
    private lateinit var moteTilhorendeArbeidstaker: no.nav.syfo.domain.PersonIdent
    private val otherArbeidstakerNoMoter = UserConstants.ARBEIDSTAKER_ANNEN_FNR

    @BeforeEach
    fun beforeEach() {
        moteRepository = MoteRepository(database = database)
        newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
        newDialogmoteNotBelongingToArbeidstaker = generateNewDialogmote(UserConstants.ARBEIDSTAKER_ANNEN_FNR)
        moteTilhorendeArbeidstaker = newDialogmote.arbeidstaker.personIdent
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Get dialogmote with UUID")
    inner class GetDialogmote {

        @Test
        fun `Successfully get mote with uuid`() {
            val createdDialogmote = database.connection.use { connection ->
                connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                connection.createNewDialogmoteWithReferences(newDialogmoteNotBelongingToArbeidstaker)
            }

            val retrievedMote = moteRepository.getMote(createdDialogmote.dialogmoteIdPair.second)

            assertEquals(1, retrievedMote.size)
            assertEquals(newDialogmote.opprettetAv, retrievedMote.first().opprettetAv)
            assertEquals(newDialogmote.status.name, retrievedMote.first().status)
            assertEquals(newDialogmote.tildeltEnhet, retrievedMote.first().tildeltEnhet)
            assertEquals(newDialogmote.tildeltVeilederIdent, retrievedMote.first().tildeltVeilederIdent)
        }

        @Test
        fun `Successfully get moter belonging to person with person ident`() {
            database.connection.use { connection ->
                connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                connection.createNewDialogmoteWithReferences(newDialogmoteNotBelongingToArbeidstaker)
            }

            val retrievedMoter = moteRepository.getMoterFor(moteTilhorendeArbeidstaker)

            assertEquals(1, retrievedMoter.size)
            assertEquals(newDialogmote.opprettetAv, retrievedMoter.first().opprettetAv)
            assertEquals(newDialogmote.status.name, retrievedMoter.first().status)
            assertEquals(newDialogmote.tildeltEnhet, retrievedMoter.first().tildeltEnhet)
            assertEquals(newDialogmote.tildeltVeilederIdent, retrievedMoter.first().tildeltVeilederIdent)
        }

        @Test
        fun `Returns empty list when no uuid exists`() {
            val retrievedMote = moteRepository.getMote(UUID.randomUUID())
            assertEquals(emptyList<Any>(), retrievedMote)
        }

        @Test
        fun `Returns empty list when person ident does not exist`() {
            val retrievedMote = moteRepository.getMoterFor(otherArbeidstakerNoMoter)
            assertEquals(emptyList<Any>(), retrievedMote)
        }
    }
}
