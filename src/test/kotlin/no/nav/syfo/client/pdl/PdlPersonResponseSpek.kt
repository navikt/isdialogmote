package no.nav.syfo.client.pdl

import no.nav.syfo.testhelper.generator.generatePdlPersonResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlPersonResponseSpek : Spek({
    describe(PdlPersonResponse::class.java.simpleName) {
        it("isKode6Or7 returns true with gradering FORTROLIG") {
            val pdlPerson = generatePdlPersonResponse(Gradering.FORTROLIG).data
            pdlPerson.isKode6Or7() shouldBeEqualTo true
        }
        it("isKode6Or7 returns true with gradering STRENGT_FORTROLIG") {
            val pdlPerson = generatePdlPersonResponse(Gradering.STRENGT_FORTROLIG).data
            pdlPerson.isKode6Or7() shouldBeEqualTo true
        }
        it("isKode6Or7 returns true with gradering STRENGT_FORTROLIG_UTLAND") {
            val pdlPerson = generatePdlPersonResponse(Gradering.STRENGT_FORTROLIG_UTLAND).data
            pdlPerson.isKode6Or7() shouldBeEqualTo true
        }
        it("isKode6Or7 returns false with gradering UGRADERT") {
            val pdlPerson = generatePdlPersonResponse(Gradering.UGRADERT).data
            pdlPerson.isKode6Or7() shouldBeEqualTo false
        }
        it("isKode6Or7 returns false when person is null") {
            val pdlPerson = PdlHentPerson(hentPerson = null)
            pdlPerson.isKode6Or7() shouldBeEqualTo false
        }
        it("isKode6Or7 returns false when person adressebeskyttelse is empty") {
            val pdlPerson = PdlHentPerson(hentPerson = PdlPerson(emptyList(), emptyList()))
            pdlPerson.isKode6Or7() shouldBeEqualTo false
        }
    }
})
