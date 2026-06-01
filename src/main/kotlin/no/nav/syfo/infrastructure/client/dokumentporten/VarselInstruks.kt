package no.nav.syfo.infrastructure.client.dokumentporten

import kotlin.String
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.ereg.EregVirksomhetsnavn

private const val KILDE = "isdialogmote"
private const val VARSEL_TEKST_INNKALT = "Viktig: Innkalling til dialogmøte med Nav"
private const val VARSEL_TEKST_AVLYST = "Viktig: Dialogmøte med Nav er avlyst"
private const val VARSEL_TEKST_EMAIL_TITTEL_NYTT_TID_STED = "Viktig: Dialogmøte med Nav er endret"
private const val VARSEL_TEKST_REFERAT = "Referat fra dialogmøte med Nav"

private const val EMAIL_TITTEL_INNKALT = "Viktig: Innkalling til dialogmøte med Nav - {mottaker}"
private const val EMAIL_TITTEL_NYTT_TID_STED = "Viktig: Dialogmøte med Nav er endret - {mottaker}"
private const val EMAIL_TITTEL_AVLYST = "Viktig: Dialogmøte med Nav er avlyst - {mottaker}"
private const val EMAIL_TITTEL_REFERAT = "Referat fra dialogmøte med Nav - {mottaker}"
private const val SIGNATUR = "Vennlig hilsen Nav"

private const val MOTTAKER_PLACEHOLDER = "{mottaker}"

private val EMAIL_BODY_INNKALT = """
    <p>Hei,
    Nav har kalt inn til et dialogmøte for en av deres ansatte. Når nærmeste leder ikke er registrert hos Nav, går innkallingen til bedriften.
    Du mottar denne beskjeden fordi du er satt opp for å motta varsler om dialogmøter i Altinn for {mottaker}.

    <p>Hva må du gjøre nå?</p>
    <ul>
        <li>Logg deg inn i Altinn eller på Min side arbeidsgiver hos Nav for å lese innkalling.</li>
        <li>Sørg for at lederen som skal delta i møtet får videresendt innkalling.</li>
        <li>Vurder å få ditt firma til å registrere nærmeste leder for den sykmeldte</li>
    </ul>

    <p>$SIGNATUR</p>
""".trimIndent()
private val EMAIL_BODY_NYTT_TID_STED = """
    <p>Hei,
    Nav har flyttet tid eller sted for et dialogmøte for en av deres ansatte. Når nærmeste leder ikke er registrert hos Nav, går endringen til bedriften.
    Du mottar denne beskjeden fordi du er satt opp for å motta varsler om dialogmøter i Altinn for {mottaker}.

    <p>Hva må du gjøre nå?</p>
    <ul>
        <li>Logg deg inn i Altinn eller på Min side arbeidsgiver hos Nav for å lese endringen.</li>
        <li>Sørg for at lederen som skal delta i møtet får videresendt endringen.</li>
        <li>Vurder å få ditt firma til å registrere nærmeste leder for den sykmeldte</li>
    </ul>

    <p>$SIGNATUR</p>
""".trimIndent()

private val EMAIL_BODY_AVLYST = """
    <p>Hei,
    Nav har avlyst et dialogmøte for en av deres ansatte. Når nærmeste leder ikke er registrert hos Nav, går avlysningen til bedriften.
    Du mottar denne beskjeden fordi du er satt opp for å motta varsler om dialogmøter i Altinn for {mottaker}.

    <p>Hva må du gjøre nå?</p>
    <ul>
        <li>Logg deg inn i Altinn eller på Min side arbeidsgiver hos Nav for å lese avlysningen.</li>
        <li>Sørg for at lederen som skal delta i møtet får videresendt avlysningen.</li>
        <li>Vurder å få ditt firma til å registrere nærmeste leder for den sykmeldte</li>
    </ul>
    
    <p>$SIGNATUR</p>
""".trimIndent()

private val EMAIL_BODY_REFERAT = """
    <p>Hei,
    Nav har sendt referat fra et dialogmøte for en av deres ansatte. Når nærmeste leder ikke er registrert hos Nav, går referatet til bedriften.
    Du mottar denne beskjeden fordi du er satt opp for å motta varsler om dialogmøter i Altinn for {mottaker}.

    <p>Hva må du gjøre nå?</p>
    <ul>
        <li>Logg deg inn i Altinn eller på Min side arbeidsgiver hos Nav for å lese referatet.</li>
        <li>Sørg for at lederen som skal delta i møtet får videresendt avlysningen.</li>
        <li>Vurder å få ditt firma til å registrere nærmeste leder for den sykmeldte</li>
    </ul>
    
    <p>$SIGNATUR</p>
""".trimIndent()

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
    
    Referatet er nå tilgjengelig.

    Du kan lese referatet i {mottaker} sin innboks i Altinn, eller ved å logge inn på Min side arbeidsgiver hos Nav.

    $SIGNATUR
""".trimIndent()

private fun toEmailTitle(
    varseltype: MotedeltakerVarselType,
    virksomhetsnummer: Virksomhetsnummer,
    virksomhetsnavn: EregVirksomhetsnavn?
): String {
    val template = when (varseltype) {
        MotedeltakerVarselType.INNKALT -> EMAIL_TITTEL_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> EMAIL_TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> EMAIL_TITTEL_AVLYST
        MotedeltakerVarselType.REFERAT -> EMAIL_TITTEL_REFERAT
    }
    return template.withMottaker(toMottaker(virksomhetsnummer, virksomhetsnavn))
}

private fun toVarselTekst(
    varseltype: MotedeltakerVarselType,
): String =
    when (varseltype) {
        MotedeltakerVarselType.INNKALT -> VARSEL_TEKST_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> VARSEL_TEKST_EMAIL_TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> VARSEL_TEKST_AVLYST
        MotedeltakerVarselType.REFERAT -> VARSEL_TEKST_REFERAT
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
                    epostTittel = toEmailTitle(varselType, virksomhetsnummer, virksomhetsnavn),
                    epostBody = toEmailBody(varselType, virksomhetsnummer, virksomhetsnavn),
                    smsTekst = toSMSBody(varselType, virksomhetsnummer, virksomhetsnavn),
                    varselTekst = toVarselTekst(varselType)
                ),
                kilde = KILDE
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
