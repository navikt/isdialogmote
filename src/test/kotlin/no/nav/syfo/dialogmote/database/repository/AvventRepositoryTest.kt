package no.nav.syfo.dialogmote.database.repository

import no.nav.syfo.infrastructure.database.repository.AvventRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateAvvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AvventRepositoryTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val avventRepository = AvventRepository(database = database)

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    @Test
    fun `persist and getAvvent returns persisted avvent by uuid`() {
        val avvent = generateAvvent()
        avventRepository.persist(avvent)

        val retrieved = avventRepository.getAvvent(avvent.uuid)

        assertNotNull(retrieved)
        assertEquals(avvent.uuid, retrieved!!.uuid)
        assertEquals(avvent.personident, retrieved.personident)
        assertEquals(avvent.createdBy, retrieved.createdBy)
        assertEquals(avvent.beskrivelse, retrieved.beskrivelse)
        assertEquals(avvent.frist, retrieved.frist)
        assertEquals(false, retrieved.isLukket)
    }

    @Test
    fun `getAvvent returns null when uuid does not exist`() {
        val result = avventRepository.getAvvent(java.util.UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `getActiveAvvent returns active avvent for personident`() {
        val avvent = generateAvvent()
        avventRepository.persist(avvent)

        val retrieved = avventRepository.getActiveAvvent(avvent.personident)

        assertNotNull(retrieved)
        assertEquals(avvent.uuid, retrieved!!.uuid)
        assertEquals(false, retrieved.isLukket)
    }

    @Test
    fun `getActiveAvvent returns null when no active avvent exists for personident`() {
        val result = avventRepository.getActiveAvvent(UserConstants.ARBEIDSTAKER_FNR)
        assertNull(result)
    }

    @Test
    fun `getActiveAvvent returns null when avvent is lukket`() {
        val avvent = generateAvvent()
        avventRepository.persist(avvent)
        avventRepository.setLukket(avvent.uuid)

        val result = avventRepository.getActiveAvvent(avvent.personident)
        assertNull(result)
    }

    @Test
    fun `getActiveAvvent returns null for other personident`() {
        val avvent = generateAvvent(personident = UserConstants.ARBEIDSTAKER_FNR)
        avventRepository.persist(avvent)

        val result = avventRepository.getActiveAvvent(UserConstants.ARBEIDSTAKER_ANNEN_FNR)
        assertNull(result)
    }

    @Test
    fun `setLukket marks avvent as lukket`() {
        val avvent = generateAvvent()
        avventRepository.persist(avvent)

        avventRepository.setLukket(avvent.uuid)

        val retrieved = avventRepository.getAvvent(avvent.uuid)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isLukket)
    }
}
