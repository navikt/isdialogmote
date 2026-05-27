package no.nav.syfo.infrastructure.client.dokumentporten

import kotlin.test.assertContains
import kotlin.test.assertFalse
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.ereg.EregVirksomhetsnavn
import org.junit.jupiter.api.Test

class VarselInstruksTest {
    @Test
    fun `opprettForVarselType bruker virksomhetsdata i epost og sms`() {
        val virksomhetsnummer = Virksomhetsnummer("123456785")
        val virksomhetsnavn = EregVirksomhetsnavn("Testbedrift AS")

        val varselInstruks = VarselInstruks.opprettForVarselType(
            varselType = MotedeltakerVarselType.INNKALT,
            virksomhetsnummer = virksomhetsnummer,
            virksomhetsnavn = virksomhetsnavn,
        )

        assertContains(varselInstruks.notifikasjonInnhold.epostBody, "Testbedrift AS (${virksomhetsnummer.value})")
        assertContains(varselInstruks.notifikasjonInnhold.smsTekst, "Testbedrift AS (${virksomhetsnummer.value})")
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("\$reporteeName$"))
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("\$reporteeNumber$"))
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("\$reporteeName$"))
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("\$reporteeNumber$"))
    }

    @Test
    fun `opprettForVarselType bruker tom streng nar virksomhetsnavn er null`() {
        val virksomhetsnummer = Virksomhetsnummer("123456785")

        val varselInstruks = VarselInstruks.opprettForVarselType(
            varselType = MotedeltakerVarselType.REFERAT,
            virksomhetsnummer = virksomhetsnummer,
            virksomhetsnavn = null,
        )

        assertContains(varselInstruks.notifikasjonInnhold.epostBody, "Virksomhet med orgnummer ${virksomhetsnummer.value}")
        assertContains(varselInstruks.notifikasjonInnhold.smsTekst, "Virksomhet med orgnummer ${virksomhetsnummer.value}")
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("null"))
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("null"))
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("\$reporteeName$"))
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("\$reporteeNumber$"))
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("\$reporteeName$"))
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("\$reporteeNumber$"))
    }
}
