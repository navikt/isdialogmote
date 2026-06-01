package no.nav.syfo.infrastructure.client.dokumentporten

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.ereg.EregVirksomhetsnavn
import org.junit.jupiter.api.Test

class VarselInstruksTest {
    private val virksomhetsnummer = Virksomhetsnummer("123456785")
    private val virksomhetsnavn = EregVirksomhetsnavn("Testbedrift AS")

    @Test
    fun `forventninger dekker alle MotedeltakerVarselType`() {
        val varselTyperIForventninger = forventninger.map { it.varselType }
        val alleVarselTyper = MotedeltakerVarselType.entries

        assertEquals(alleVarselTyper.size, varselTyperIForventninger.size)
        assertEquals(alleVarselTyper.toSet(), varselTyperIForventninger.toSet())
    }

    @Test
    fun `opprettForVarselType oppretter forventet innhold med virksomhetsnavn`() {
        val mottaker = "Testbedrift AS (${virksomhetsnummer.value})"

        forventninger.forEach { forventning ->
            assertVarselInstruks(
                scenario = scenarioBeskrivelse(
                    varselType = forventning.varselType,
                    medVirksomhetsnavn = true,
                ),
                forventning = forventning,
                virksomhetsnavn = virksomhetsnavn,
                forventetMottaker = mottaker,
            )
        }
    }

    @Test
    fun `opprettForVarselType oppretter forventet innhold uten virksomhetsnavn`() {
        val mottaker = "Virksomhet med orgnummer ${virksomhetsnummer.value}"

        forventninger.forEach { forventning ->
            assertVarselInstruks(
                scenario = scenarioBeskrivelse(
                    varselType = forventning.varselType,
                    medVirksomhetsnavn = false,
                ),
                forventning = forventning,
                virksomhetsnavn = null,
                forventetMottaker = mottaker,
            )
        }
    }

    private fun assertVarselInstruks(
        scenario: String,
        forventning: VarselInstruksForventning,
        virksomhetsnavn: EregVirksomhetsnavn?,
        forventetMottaker: String,
    ) {
        val varselInstruks = VarselInstruks.opprettForVarselType(
            varselType = forventning.varselType,
            virksomhetsnummer = virksomhetsnummer,
            virksomhetsnavn = virksomhetsnavn,
        )

        assertEquals(HendelseType.AG_VARSEL_ALTINN_RESSURS, varselInstruks.type, scenario)
        assertEquals("isdialogmote", varselInstruks.kilde, scenario)
        assertEquals(
            forventning.epostTittelTemplate.withMottaker(forventetMottaker),
            varselInstruks.notifikasjonInnhold.epostTittel,
            scenario,
        )
        assertEquals(forventning.varselTekst, varselInstruks.notifikasjonInnhold.varselTekst, scenario)
        assertEquals(
            forventning.epostBodyTemplate.withMottaker(forventetMottaker),
            varselInstruks.notifikasjonInnhold.epostBody,
            scenario,
        )
        assertEquals(
            forventning.smsTekstTemplate.withMottaker(forventetMottaker),
            varselInstruks.notifikasjonInnhold.smsTekst,
            scenario,
        )
        assertContains(varselInstruks.notifikasjonInnhold.epostBody, forventetMottaker, message = scenario)
        assertContains(varselInstruks.notifikasjonInnhold.smsTekst, forventetMottaker, message = scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.epostTittel.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.varselTekst.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.epostTittel.contains("null"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("null"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("null"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.varselTekst.contains("null"), scenario)
    }

    private fun scenarioBeskrivelse(
        varselType: MotedeltakerVarselType,
        medVirksomhetsnavn: Boolean,
    ): String =
        "Scenario[varselType=$varselType, virksomhetsnavn=${if (medVirksomhetsnavn) "med" else "uten"}]"

    private data class VarselInstruksForventning(
        val varselType: MotedeltakerVarselType,
        val epostTittelTemplate: String,
        val varselTekst: String,
        val epostBodyTemplate: String,
        val smsTekstTemplate: String,
    )

    private fun String.withMottaker(mottaker: String): String = replace("{mottaker}", mottaker)

    private val forventninger = listOf(
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.INNKALT,
            epostTittelTemplate = EMAIL_TITTEL_INNKALT,
            varselTekst = VARSEL_TEKST_INNKALT,
            epostBodyTemplate = EMAIL_BODY_INNKALT,
            smsTekstTemplate = SMS_BODY_INNKALT,
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.NYTT_TID_STED,
            epostTittelTemplate = EMAIL_TITTEL_NYTT_TID_STED,
            varselTekst = VARSEL_TEKST_EMAIL_TITTEL_NYTT_TID_STED,
            epostBodyTemplate = EMAIL_BODY_NYTT_TID_STED,
            smsTekstTemplate = SMS_BODY_NYTT_TID_STED,
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.AVLYST,
            epostTittelTemplate = EMAIL_TITTEL_AVLYST,
            varselTekst = VARSEL_TEKST_AVLYST,
            epostBodyTemplate = EMAIL_BODY_AVLYST,
            smsTekstTemplate = SMS_BODY_AVLYST,
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.REFERAT,
            epostTittelTemplate = EMAIL_TITTEL_REFERAT,
            varselTekst = VARSEL_TEKST_REFERAT,
            epostBodyTemplate = EMAIL_BODY_REFERAT,
            smsTekstTemplate = SMS_BODY_REFERAT,
        ),
    )
}
