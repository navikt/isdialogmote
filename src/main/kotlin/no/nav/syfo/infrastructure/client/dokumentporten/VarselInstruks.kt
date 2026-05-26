package no.nav.syfo.infrastructure.client.dokumentporten

import kotlin.String
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType

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

private val SMS_BODY_INNKALT =
    """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. Logg inn i Altinn for å lese innkallingen. 
    """.trimIndent()
private val SMS_BODY_NYTT_TID_STED =
    """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) er innkalt til dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. NAV har endret tidspunktet eller stedet for dialogmøtet.
    Logg inn i Altinn for å lese endringen. 
    """.trimIndent()
private var SMS_BODY_AVLYST =
    """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) var kalt inn til et dialogmøte med NAV i forbindelse med 
    sykefraværet til en av deres ansatte. Dette har blitt avlyst. Logg inn i Altinn for å lese
    avlysningen. 
    """.trimIndent()
private val SMS_BODY_REFERAT =
    """
    $ALTINN_VAR_REPORTEE_NAME ($ALTINN_VAR_REPORTEE_NUMBER) har vært i dialogmøte med NAV i forbindelse med
    sykefraværet til en av deres ansatte. Logg inn i Altinn for å lese referatet. 
    """.trimIndent()

data class VarselInstruks(
    val type: HendelseType,
    val notifikasjonInnhold: NotifikasjonInnhold,
    val kilde: String,
) {
    companion object {
        fun opprettForVarselType(varselType: MotedeltakerVarselType): VarselInstruks {
            return VarselInstruks(
                type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
                notifikasjonInnhold = NotifikasjonInnhold(
                    epostTittel = toEmailTitle(varselType),
                    epostBody = toEmailBody(varselType),
                    smsTekst = toSMSBody(varselType),
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
)

enum class HendelseType {
    AG_VARSEL_ALTINN_RESSURS,
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
