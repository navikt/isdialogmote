package no.nav.syfo.client.altinn

import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.util.*

private const val TITTEL_INNKALT = "Innkalling til dialogmøte"
private const val TITTEL_NYTT_TID_STED = "Endring av dialogmøte"
private const val TITTEL_AVLYST = "Avlysning av dialogmøte"
private const val TITTEL_REFERAT = "Referat fra dialogmøte"

private const val TITTEL_INNKALT_COPY = "Innkalling til dialogmøte (kopi)"
private const val TITTEL_NYTT_TID_STED_COPY = "Endring av dialogmøte (kopi)"
private const val TITTEL_AVLYST_COPY = "Avlysning av dialogmøte (kopi)"
private const val TITTEL_REFERAT_COPY = "Referat fra dialogmøte (kopi)"

private const val FILNAVN_INNKALT = "Innkalling.pdf"
private const val FILNAVN_NYTT_TID_STED = "Endring.pdf"
private const val FILNAVN_AVLYST = "Avlysning.pdf"
private const val FILNAVN_REFERAT = "Referat.pdf"

private const val BODY_FERDIGSTILL = ""
private const val BODY_KREVER_HANDLING = """
    Det er ikke registrert en nærmeste leder for denne arbeidstakeren. For å svare på innkallingen må det registreres
    en leder, og deretter kan lederen gå inn på Dine Sykmeldte hos NAV.
"""
private const val BODY_DUPLICATE_BREV = """
    Dette er en kopi av et brev som er tilgjengelig for nærmeste leder på Dine Sykmeldte hos NAV.
"""

private const val EMAIL_TITTEL_INNKALT = "Ny innkalling til dialogmøte i Altinn"
private const val EMAIL_TITTEL_NYTT_TID_STED = "Ny endring av dialogmøte i Altinn"
private const val EMAIL_TITTEL_AVLYST = "Ny avlysning av dialogmøte i Altinn"
private const val EMAIL_TITTEL_REFERAT = "Nytt referat fra dialogmøte i Altinn"
private const val SIGNATUR = "Vennlig hilsen NAV"

private const val ALTINN_VAR_REPORTEE_NAME = "\$reporteeName$"
private const val ALTINN_VAR_REPORTEE_NUMBER = "\$reporteeNumber$"

private const val EMAIL_BODY_INNKALT = """
    <p>$ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>Logg inn i Altinn for å lese innkallingen.</p>

    <p>$SIGNATUR</p>
"""
private const val EMAIL_BODY_NYTT_TID_STED = """
    <p>$ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i  forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>NAV har endret tidspunktet eller stedet for dialogmøtet.</p>

    <p>Logg inn i Altinn for å lese endringen.</p>

    <p>$SIGNATUR</p>
"""
private const val EMAIL_BODY_AVLYST = """
    <p>$ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) var kalt inn til et dialogmøte med NAV i forbindelse med
    sykefraværet til en  av deres ansatte.</p>

    <p>Dette har blitt avlyst.</p>

    <p>Logg inn i Altinn for å lese avlysningen.</p>

    <p>$SIGNATUR</p>
"""
private const val EMAIL_BODY_REFERAT = """
    <p>$ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) har vært i dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte.</p>

    <p>Logg inn i Altinn for å lese referatet.</p>

    <p>$SIGNATUR</p>
"""

private val SMS_BODY_INNKALT = """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. Logg inn i Altinn for å lese innkallingen. 
""".trimIndent()
private val SMS_BODY_NYTT_TID_STED = """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. NAV har endret tidspunktet eller stedet for dialogmøtet.
    Logg inn i Altinn for å lese endringen. 
""".trimIndent()
private var SMS_BODY_AVLYST = """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) var kalt inn til et dialogmøte med NAV i forbindelse med 
    sykefraværet til en av deres ansatte. Dette har blitt avlyst. Logg inn i Altinn for å lese
    avlysningen. 
""".trimIndent()
private val SMS_BODY_REFERAT = """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) har vært i dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. Logg inn i Altinn for å lese referatet. 
""".trimIndent()

data class AltinnMelding(
    val reference: UUID,
    val virksomhetsnummer: Virksomhetsnummer,
    val title: String,
    val body: String,
    val emailTitle: String,
    val emailBody: String,
    val smsBody: String,
    val smsSender: String,
    val file: ByteArray,
    val filename: String,
    val displayFilename: String,
    val hasNarmesteLeder: Boolean,
)

fun createAltinnMelding(
    reference: UUID,
    virksomhetsnummer: Virksomhetsnummer,
    file: ByteArray,
    varseltype: MotedeltakerVarselType,
    arbeidstakerPersonIdent: PersonIdent,
    arbeidstakernavn: String,
    hasNarmesteLeder: Boolean,
): AltinnMelding {
    return AltinnMelding(
        reference = reference,
        virksomhetsnummer = virksomhetsnummer,
        title = toMessageTitle(varseltype, arbeidstakerPersonIdent, arbeidstakernavn, hasNarmesteLeder),
        body = toMessageBody(varseltype, hasNarmesteLeder),
        emailTitle = toEmailTitle(varseltype),
        emailBody = toEmailBody(varseltype),
        smsBody = toSMSBody(varseltype),
        smsSender = SIGNATUR,
        file = file,
        filename = toFilename(varseltype),
        displayFilename = toDisplayFilename(varseltype),
        hasNarmesteLeder = hasNarmesteLeder
    )
}

private fun toVarselTypeTitle(varseltype: MotedeltakerVarselType, isCopy: Boolean): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> TITTEL_INNKALT_COPY.takeIf { isCopy } ?: TITTEL_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> TITTEL_NYTT_TID_STED_COPY.takeIf { isCopy } ?: TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> TITTEL_AVLYST_COPY.takeIf { isCopy } ?: TITTEL_AVLYST
        MotedeltakerVarselType.REFERAT -> TITTEL_REFERAT_COPY.takeIf { isCopy } ?: TITTEL_REFERAT
    }
}

private fun toMessageTitle(
    varseltype: MotedeltakerVarselType,
    personIdent: PersonIdent,
    navn: String?,
    isCopy: Boolean,
): String {
    return "${toVarselTypeTitle(varseltype, isCopy)} - $navn (${personIdent.value})"
}

private fun toDisplayFilename(varseltype: MotedeltakerVarselType): String {
    return toVarselTypeTitle(varseltype, false)
}

private fun toFilename(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> FILNAVN_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> FILNAVN_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> FILNAVN_AVLYST
        MotedeltakerVarselType.REFERAT -> FILNAVN_REFERAT
    }
}

private fun toMessageBody(varseltype: MotedeltakerVarselType, isCopy: Boolean): String {
    if (isCopy) {
        return BODY_DUPLICATE_BREV
    }

    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> BODY_KREVER_HANDLING
        MotedeltakerVarselType.NYTT_TID_STED -> BODY_KREVER_HANDLING
        MotedeltakerVarselType.AVLYST -> BODY_FERDIGSTILL
        MotedeltakerVarselType.REFERAT -> BODY_FERDIGSTILL
    }
}

private fun toEmailTitle(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> EMAIL_TITTEL_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> EMAIL_TITTEL_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> EMAIL_TITTEL_AVLYST
        MotedeltakerVarselType.REFERAT -> EMAIL_TITTEL_REFERAT
    }
}

private fun toEmailBody(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> EMAIL_BODY_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> EMAIL_BODY_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> EMAIL_BODY_AVLYST
        MotedeltakerVarselType.REFERAT -> EMAIL_BODY_REFERAT
    }
}

private fun toSMSBody(varseltype: MotedeltakerVarselType): String {
    return when (varseltype) {
        MotedeltakerVarselType.INNKALT -> SMS_BODY_INNKALT
        MotedeltakerVarselType.NYTT_TID_STED -> SMS_BODY_NYTT_TID_STED
        MotedeltakerVarselType.AVLYST -> SMS_BODY_AVLYST
        MotedeltakerVarselType.REFERAT -> SMS_BODY_REFERAT
    }
}
