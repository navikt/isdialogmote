package no.nav.syfo.testdata.reset

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestdataResetServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val testdataResetService = TestdataResetService(
        database = database,
    )

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `Skal slette dialogmøte fra database for oppgitt arbeidstaker`() {
        // Populate database with new dialogmote for arbeidstaker
        val newDialogmote = generateNewDialogmote(personIdent = ARBEIDSTAKER_FNR)
        database.connection.use { connection ->
            connection.createNewDialogmoteWithReferences(
                newDialogmote = newDialogmote,
                commit = true,
            )
        }

        // Check that arbeidstaker exist in db before update
        assertEquals(1, database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size)

        runBlocking {
            testdataResetService.resetTestdata(ARBEIDSTAKER_FNR)
        }

        // Check that arbeidstaker do not exist in db after update
        assertEquals(0, database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size)
    }

    @Test
    fun `Skal ikke slette dialogmøte på annen arbeidstaker`() {
        // Populate database with new dialogmote for arbeidstaker
        val newDialogmote = generateNewDialogmote(personIdent = ARBEIDSTAKER_FNR)
        database.connection.use { connection ->
            connection.createNewDialogmoteWithReferences(
                newDialogmote = newDialogmote,
                commit = true,
            )
        }

        // Check that arbeidstaker exist in db before update
        assertEquals(1, database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size)

        // Delete other arbeidstaker
        runBlocking {
            testdataResetService.resetTestdata(ARBEIDSTAKER_ANNEN_FNR)
        }

        // Check that arbeidstaker still exist in db after update
        assertEquals(1, database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size)
    }
}
