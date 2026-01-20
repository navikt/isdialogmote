package no.nav.syfo.dialogmote.database.repository

import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.repository.MoteRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class MoteRepositoryTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteRepository = MoteRepository(database = database)
    private val newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
    private val newDialogmoteNotBelongingToArbeidstaker = generateNewDialogmote(UserConstants.ARBEIDSTAKER_ANNEN_FNR)
    private val moteTilhorendeArbeidstaker = newDialogmote.arbeidstaker.personIdent
    private val otherArbeidstakerNoMoter = UserConstants.ARBEIDSTAKER_ANNEN_FNR

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    @Test
    fun `Successfully get mote with uuid`() {
        val createdDialogmote = database.connection.use { connection ->
            connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
            connection.createNewDialogmoteWithReferences(newDialogmoteNotBelongingToArbeidstaker)
        }

        val retrievedMote = moteRepository.getMote(createdDialogmote.dialogmoteIdPair.second)

        assertEquals(newDialogmote.opprettetAv, retrievedMote.opprettetAv)
        assertEquals(newDialogmote.status.name, retrievedMote.status)
        assertEquals(newDialogmote.tildeltEnhet, retrievedMote.tildeltEnhet)
        assertEquals(newDialogmote.tildeltVeilederIdent, retrievedMote.tildeltVeilederIdent)
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
        assertThrows<NoSuchElementException> {
            moteRepository.getMote(UUID.randomUUID())
        }
    }

    @Test
    fun `Returns empty list when person ident does not exist`() {
        val retrievedMote = moteRepository.getMoterFor(otherArbeidstakerNoMoter)
        assertEquals(emptyList<Any>(), retrievedMote)
    }
}
