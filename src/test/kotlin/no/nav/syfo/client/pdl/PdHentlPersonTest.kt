package no.nav.syfo.client.pdl

import no.nav.syfo.infrastructure.client.pdl.PdlHentPerson
import no.nav.syfo.infrastructure.client.pdl.PdlPerson
import no.nav.syfo.infrastructure.client.pdl.PdlPersonNavn
import no.nav.syfo.infrastructure.client.pdl.fullName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdHentlPersonTest {

    @Test
    fun `should return full name`() {
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
        assertEquals("Ola Mellomnavn Nordmann", pdlHentPerson.fullName())
    }

    @Test
    fun `should return full name with spaces`() {
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
        assertEquals("Ola Tonavn Mellomnavn Nordmann", pdlHentPerson.fullName())
    }

    @Test
    fun `should return full name with dash`() {
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
        assertEquals("Ola-Tonavn Mellomnavn Nordmann", pdlHentPerson.fullName())
    }
}
