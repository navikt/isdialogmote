package no.nav.syfo.dialogmote.database

import io.ktor.server.testing.*
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class MoteRepositorySpek : Spek({

    describe(MoteRepositorySpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val moteRepository = MoteRepository(database = database)
            val newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
            val moteTilhorendeArbeidstaker = newDialogmote.arbeidstaker.personIdent

            afterEachTest {
                database.dropData()
            }

            describe("Get dialogmote with UUID") {

                describe("Happy path") {

                    it("Successfully get mote with uuid") {
                        val createdDialogmote = database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                        }

                        val retrievedMote = moteRepository.getMote(createdDialogmote.dialogmoteIdPair.second)

                        retrievedMote.first().opprettetAv shouldBeEqualTo newDialogmote.opprettetAv
                        retrievedMote.first().status shouldBeEqualTo newDialogmote.status.name
                        retrievedMote.first().tildeltEnhet shouldBeEqualTo newDialogmote.tildeltEnhet
                        retrievedMote.first().tildeltVeilederIdent shouldBeEqualTo newDialogmote.tildeltVeilederIdent
                    }

                    it("Successfully get moter belonging to person with person ident") {
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                        }

                        val retrievedMote = moteRepository.getMoterFor(moteTilhorendeArbeidstaker)

                        retrievedMote.first().opprettetAv shouldBeEqualTo newDialogmote.opprettetAv
                        retrievedMote.first().status shouldBeEqualTo newDialogmote.status.name
                        retrievedMote.first().tildeltEnhet shouldBeEqualTo newDialogmote.tildeltEnhet
                        retrievedMote.first().tildeltVeilederIdent shouldBeEqualTo newDialogmote.tildeltVeilederIdent
                    }
                }
            }
        }
    }
})
