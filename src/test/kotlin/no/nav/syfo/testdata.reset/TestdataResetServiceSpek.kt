package no.nav.syfo.testdata.reset

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestdataResetServiceSpek : Spek({

    describe(TestdataResetServiceSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val testdataResetService = TestdataResetService(
                database = database,
            )

            afterEachTest {
                database.dropData()
            }

            describe("Happy path") {
                it("Skal slette dialogmøte fra database for oppgitt arbeidstaker") {
                    // Populate database with new dialogmote for arbeidstaker
                    val newDialogmote = generateNewDialogmote(personIdent = ARBEIDSTAKER_FNR)
                    database.connection.use { connection ->
                        connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmote,
                            commit = true,
                        )
                    }

                    // Check that arbeidstaker exist in db before update
                    database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size shouldBeEqualTo 1

                    runBlocking {
                        testdataResetService.resetTestdata(ARBEIDSTAKER_FNR)
                    }

                    // Check that arbeidstaker do not exist in db after update
                    database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size shouldBeEqualTo 0
                }

                it("Skal ikke slette dialogmøte på annen arbeidstaker") {
                    // Populate database with new dialogmote for arbeidstaker
                    val newDialogmote = generateNewDialogmote(personIdent = ARBEIDSTAKER_FNR)
                    database.connection.use { connection ->
                        connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmote,
                            commit = true,
                        )
                    }

                    // Check that arbeidstaker exist in db before update
                    database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size shouldBeEqualTo 1

                    // Delete other arbeidstaker
                    runBlocking {
                        testdataResetService.resetTestdata(ARBEIDSTAKER_ANNEN_FNR)
                    }

                    // Check that arbeidstaker still exist in db after update
                    database.getMotedeltakerArbeidstakerByIdent(ARBEIDSTAKER_FNR).size shouldBeEqualTo 1
                }
            }
        }
    }
})
