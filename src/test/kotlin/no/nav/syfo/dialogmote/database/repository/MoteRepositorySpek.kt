package no.nav.syfo.dialogmote.database.repository

import io.ktor.server.testing.*
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MoteRepositorySpek : Spek({

    describe(MoteRepositorySpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val moteRepository = MoteRepository(database = database)
        val newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
        val newDialogmoteNotBelongingToArbeidstaker = generateNewDialogmote(UserConstants.ARBEIDSTAKER_ANNEN_FNR)
        val moteTilhorendeArbeidstaker = newDialogmote.arbeidstaker.personIdent
        val otherArbeidstakerNoMoter = UserConstants.ARBEIDSTAKER_ANNEN_FNR

        afterEachTest {
            database.dropData()
        }

        describe("Get dialogmote with UUID") {

            it("Successfully get mote with uuid") {
                val createdDialogmote = database.connection.use { connection ->
                    connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                    connection.createNewDialogmoteWithReferences(newDialogmoteNotBelongingToArbeidstaker)
                }

                val retrievedMote = moteRepository.getMote(createdDialogmote.dialogmoteIdPair.second)

                retrievedMote.size shouldBe 1
                retrievedMote.first().opprettetAv shouldBeEqualTo newDialogmote.opprettetAv
                retrievedMote.first().status shouldBeEqualTo newDialogmote.status.name
                retrievedMote.first().tildeltEnhet shouldBeEqualTo newDialogmote.tildeltEnhet
                retrievedMote.first().tildeltVeilederIdent shouldBeEqualTo newDialogmote.tildeltVeilederIdent
            }

            it("Successfully get moter belonging to person with person ident") {
                database.connection.use { connection ->
                    connection.createNewDialogmoteWithReferences(newDialogmote = newDialogmote)
                    connection.createNewDialogmoteWithReferences(newDialogmoteNotBelongingToArbeidstaker)
                }

                val retrievedMoter = moteRepository.getMoterFor(moteTilhorendeArbeidstaker)

                retrievedMoter.size shouldBe 1
                retrievedMoter.first().opprettetAv shouldBeEqualTo newDialogmote.opprettetAv
                retrievedMoter.first().status shouldBeEqualTo newDialogmote.status.name
                retrievedMoter.first().tildeltEnhet shouldBeEqualTo newDialogmote.tildeltEnhet
                retrievedMoter.first().tildeltVeilederIdent shouldBeEqualTo newDialogmote.tildeltVeilederIdent
            }

            it("Returns empty list when no uuid exists") {
                val retrievedMote = moteRepository.getMote(UUID.randomUUID())
                retrievedMote shouldBeEqualTo emptyList()
            }

            it("Returns empty list when person ident does not exist") {
                val retrievedMote = moteRepository.getMoterFor(otherArbeidstakerNoMoter)
                retrievedMote shouldBeEqualTo emptyList()
            }
        }
    }
})
