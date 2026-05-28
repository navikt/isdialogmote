package no.nav.syfo.infrastructure.client.dokumentporten

import kotlin.String
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.ereg.EregVirksomhetsnavn

private const val VARSEL_TEKST_INNKALT = "Innkalling til dialogmøte med Nav"
private const val VARSEL_TEKST_AVLYST = "Dialogmøte med Nav er avlyst"
private const val VARSEL_TEKST_EMAIL_TITTEL_NYTT_TID_STED = "Dialogmøte med Nav er endret"
private const val VARSEL_TEKST_REFERAT = "Referat fra dialogmøte med Nav"

private const val EMAIL_TITTEL_INNKALT = "Innkalling til dialogmøte med Nav"
private const val EMAIL_TITTEL_NYTT_TID_STED = "Dialogmøte med Nav er endret"
private const val EMAIL_TITTEL_AVLYST = "Dialogmøte med Nav er avlyst"
private const val EMAIL_TITTEL_REFERAT = "Referat fra dialogmøte med Nav"
private const val SIGNATUR = "Vennlig hilsen Nav"

private const val MOTTAKER_PLACEHOLDER = "{mottaker}"

private val EMAIL_BODY_INNKALT = """
    <p>{mottaker} er innkalt til dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>Du kan lese innkallingen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.</p>

    <p>$SIGNATUR</p>
"""

private val EMAIL_BODY_NYTT_TID_STED = """
    <p>{mottaker} er innkalt til dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>Nav har endret tidspunktet eller stedet for dialogmøtet.</p>

    <p>Du kan lese endringen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.</p>

    <p>$SIGNATUR</p>
"""

private val EMAIL_BODY_AVLYST = """
    <p>{mottaker} var kalt inn til et dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>Dialogmøtet har blitt avlyst.</p>

    <p> Du kan lese avlysningen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.</p>

    <p>$SIGNATUR</p>
"""

private val EMAIL_BODY_REFERAT = """
    <p>{mottaker} har vært i dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p> Du kan lese referatet i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.</p>

    <p>$SIGNATUR</p>
"""

private val SMS_BODY_INNKALT = """
    {mottaker} er innkalt til dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.

    Du kan lese innkallingen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.

    $SIGNATUR
""".trimIndent()

private val SMS_BODY_NYTT_TID_STED = """
    {mottaker} er innkalt til dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.

    Nav har endret tidspunktet eller stedet for dialogmøtet.

    Du kan lese endringen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.

    $SIGNATUR
""".trimIndent()

private val SMS_BODY_AVLYST = """
    {mottaker} var kalt inn til et dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.

    Dialogmøtet har blitt avlyst.

     Du kan lese avlysningen i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.

    $SIGNATUR
""".trimIndent()

private val SMS_BODY_REFERAT = """
    {mottaker} har vært i dialogmøte med Nav i forbindelse med
    sykefraværet til en av deres ansatte.

     Du kan lese referatet i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.

    $SIGNATUR
""".trimIndent()

private fun toEmailTitle(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> EMAIL_TITTEL_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> EMAIL_TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> EMAIL_TITTEL_AVLYST
        MotedeltakerVarselType.REFERAT -> EMAIL_TITTEL_REFERAT
    }
}

private fun toVarselTekst(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> VARSEL_TEKST_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> VARSEL_TEKST_EMAIL_TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> VARSEL_TEKST_AVLYST
        MotedeltakerVarselType.REFERAT -> VARSEL_TEKST_REFERAT
    }
}

private fun toMottaker(
    virksomhetsnummer: Virksomhetsnummer,
    virksomhetsnavn: EregVirksomhetsnavn?,
): String =
    virksomhetsnavn?.let {
        "${virksomhetsnavn.virksomhetsnavn} (${virksomhetsnummer.value})"
    } ?: "Virksomhet med orgnummer ${virksomhetsnummer.value}"

private fun String.withMottaker(mottaker: String): String =
    replace(MOTTAKER_PLACEHOLDER, mottaker)

private fun toEmailBody(
    varseltype: MotedeltakerVarselType,
    virksomhetsnummer: Virksomhetsnummer,
    virksomhetsnavn: EregVirksomhetsnavn?
): String {
    val template = when (varseltype) {
        MotedeltakerVarselType.INNKALT -> EMAIL_BODY_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> EMAIL_BODY_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> EMAIL_BODY_AVLYST
        MotedeltakerVarselType.REFERAT -> EMAIL_BODY_REFERAT
    }

    return template.withMottaker(toMottaker(virksomhetsnummer, virksomhetsnavn))
}

private fun toSMSBody(
    varseltype: MotedeltakerVarselType,
    virksomhetsnummer: Virksomhetsnummer,
    virksomhetsnavn: EregVirksomhetsnavn?
): String {
    val template = when (varseltype) {
        MotedeltakerVarselType.INNKALT -> SMS_BODY_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> SMS_BODY_NYTT_TID_STED.withMottaker(
            toMottaker(
                virksomhetsnummer,
                virksomhetsnavn
            )
        )

        MotedeltakerVarselType.AVLYST -> SMS_BODY_AVLYST
        MotedeltakerVarselType.REFERAT -> SMS_BODY_REFERAT
    }
    return template.withMottaker(toMottaker(virksomhetsnummer, virksomhetsnavn))
}

data class VarselInstruks(
    val type: HendelseType,
    val notifikasjonInnhold: NotifikasjonInnhold,
    val kilde: String,
) {
    companion object {
        fun opprettForVarselType(
            varselType: MotedeltakerVarselType,
            virksomhetsnummer: Virksomhetsnummer,
            virksomhetsnavn: EregVirksomhetsnavn?,
        ): VarselInstruks {
            return VarselInstruks(
                type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
                notifikasjonInnhold = NotifikasjonInnhold(
                    epostTittel = toEmailTitle(varselType),
                    epostBody = toEmailBody(varselType, virksomhetsnummer, virksomhetsnavn),
                    smsTekst = toSMSBody(varselType, virksomhetsnummer, virksomhetsnavn),
                    varselTekst = toVarselTekst(varselType)
                ),
                kilde = "DIALOGMOTE"
            )
        }
    }
}

data class NotifikasjonInnhold(
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
    val varselTekst: String,
)

enum class HendelseType {
    AG_VARSEL_ALTINN_RESSURS,
}
