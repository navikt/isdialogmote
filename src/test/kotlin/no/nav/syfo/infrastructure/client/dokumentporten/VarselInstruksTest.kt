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
        assertEquals("DIALOGMOTE", varselInstruks.kilde, scenario)
        assertEquals(forventning.epostTittel, varselInstruks.notifikasjonInnhold.epostTittel, scenario)
        assertEquals(forventning.varselTekst, varselInstruks.notifikasjonInnhold.varselTekst, scenario)
        assertContains(varselInstruks.notifikasjonInnhold.epostBody, forventetMottaker, message = scenario)
        assertContains(varselInstruks.notifikasjonInnhold.smsTekst, forventetMottaker, message = scenario)
        forventning.epostFragmenter.forEach {
            assertContains(varselInstruks.notifikasjonInnhold.epostBody, it, message = scenario)
        }
        forventning.smsFragmenter.forEach {
            assertContains(varselInstruks.notifikasjonInnhold.smsTekst, it, message = scenario)
        }
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("{mottaker}"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.epostBody.contains("null"), scenario)
        assertFalse(varselInstruks.notifikasjonInnhold.smsTekst.contains("null"), scenario)
    }

    private fun scenarioBeskrivelse(
        varselType: MotedeltakerVarselType,
        medVirksomhetsnavn: Boolean,
    ): String =
        "Scenario[varselType=$varselType, virksomhetsnavn=${if (medVirksomhetsnavn) "med" else "uten"}]"

    private data class VarselInstruksForventning(
        val varselType: MotedeltakerVarselType,
        val epostTittel: String,
        val varselTekst: String,
        val epostFragmenter: List<String>,
        val smsFragmenter: List<String>,
    )

    private val forventninger = listOf(
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.INNKALT,
            epostTittel = "Innkalling til dialogmøte med Nav",
            varselTekst = "Innkalling til dialogmøte med Nav",
            epostFragmenter = listOf(
                "er innkalt til dialogmøte med Nav",
                "Du kan lese innkallingen",
            ),
            smsFragmenter = listOf(
                "er innkalt til dialogmøte med Nav",
                "Du kan lese innkallingen",
            ),
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.NYTT_TID_STED,
            epostTittel = "Dialogmøte med Nav er endret",
            varselTekst = "Dialogmøte med Nav er endret",
            epostFragmenter = listOf(
                "Nav har endret tidspunktet eller stedet for dialogmøtet.",
                "Du kan lese endringen",
            ),
            smsFragmenter = listOf(
                "Nav har endret tidspunktet eller stedet for dialogmøtet.",
                "Du kan lese endringen",
            ),
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.AVLYST,
            epostTittel = "Dialogmøte med Nav er avlyst",
            varselTekst = "Dialogmøte med Nav er avlyst",
            epostFragmenter = listOf(
                "Dialogmøtet har blitt avlyst.",
                "Du kan lese avlysningen",
            ),
            smsFragmenter = listOf(
                "Dialogmøtet har blitt avlyst.",
                "Du kan lese avlysningen",
            ),
        ),
        VarselInstruksForventning(
            varselType = MotedeltakerVarselType.REFERAT,
            epostTittel = "Referat fra dialogmøte med Nav",
            varselTekst = "Referat fra dialogmøte med Nav",
            epostFragmenter = listOf(
                "har vært i dialogmøte med Nav",
                "Du kan lese referatet",
            ),
            smsFragmenter = listOf(
                "har vært i dialogmøte med Nav",
                "Du kan lese referatet",
            ),
        ),
    )
}
