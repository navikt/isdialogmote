package no.nav.syfo.client.pdl

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PdHentlPersonSpek : Spek({
    describe(PdlPerson::class.java.simpleName) {
        it("should return full name") {
            val pdlHentPerson = PdlHentPerson(
                hentPerson = PdlPerson(
                    navn = listOf(
                        PdlPersonNavn(
                            fornavn = "OLA",
                            mellomnavn = "mELLOMNAVN",
                            etternavn = "nordmann",
                        )
                    )
                )
            )
            pdlHentPerson.fullName() shouldBeEqualTo "Ola Mellomnavn Nordmann"
        }

        it("should return full name with spaces") {
            val pdlHentPerson = PdlHentPerson(
                hentPerson = PdlPerson(
                    navn = listOf(
                        PdlPersonNavn(
                            fornavn = "OLA TONAVN",
                            mellomnavn = "mellomnavn",
                            etternavn = "nordmann",
                        )
                    )
                )
            )
            pdlHentPerson.fullName() shouldBeEqualTo "Ola Tonavn Mellomnavn Nordmann"
        }

        it("should return full name with dash") {
            val pdlHentPerson = PdlHentPerson(
                hentPerson = PdlPerson(
                    navn = listOf(
                        PdlPersonNavn(
                            fornavn = "OLA-TONAVN",
                            mellomnavn = "mellomnavn",
                            etternavn = "nordmann",
                        )
                    )
                )
            )
            pdlHentPerson.fullName() shouldBeEqualTo "Ola-Tonavn Mellomnavn Nordmann"
        }
    }
})
