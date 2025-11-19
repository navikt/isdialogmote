package no.nav.syfo.infrastructure.client.arkivporten

import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType

private const val TITTEL_INNKALT = "Innkalling til dialogmøte"
private const val TITTEL_NYTT_TID_STED = "Endring av dialogmøte"
private const val TITTEL_AVLYST = "Avlysning av dialogmøte"
private const val TITTEL_REFERAT = "Referat fra dialogmøte"

private const val TITTEL_INNKALT_COPY = "Innkalling til dialogmøte (kopi)"
private const val TITTEL_NYTT_TID_STED_COPY = "Endring av dialogmøte (kopi)"
private const val TITTEL_AVLYST_COPY = "Avlysning av dialogmøte (kopi)"
private const val TITTEL_REFERAT_COPY = "Referat fra dialogmøte (kopi)"

private const val BODY_FERDIGSTILL = ""
private const val BODY_KREVER_HANDLING = """
    Det er ikke registrert en nærmeste leder for denne arbeidstakeren. For å svare på innkallingen må det registreres
    en leder, og deretter kan lederen gå inn på Dine Sykmeldte hos NAV.
"""
private const val BODY_DUPLICATE_BREV = """
    Dette er en kopi av et brev som er tilgjengelig for nærmeste leder på Dine Sykmeldte hos NAV.
"""

data class ArkivportenDocument(
    val documentId: UUID,
    val type: DocumentType,
    val content: ByteArray,
    val contentType: ContentType,
    val orgnumber: String,
    val dialogTitle: String,
    val dialogSummary: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArkivportenDocument

        if (type != other.type) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false
        if (orgnumber != other.orgnumber) return false
        if (dialogTitle != other.dialogTitle) return false
        if (dialogSummary != other.dialogSummary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + orgnumber.hashCode()
        result = 31 * result + dialogTitle.hashCode()
        result = 31 * result + dialogSummary.hashCode()
        return result
    }

    enum class DocumentType {
        DIALOGMOTE,
    }

    enum class ContentType(val value: String) {
        PDF("application/pdf");

        @JsonValue
        override fun toString() = value
    }
}


fun createArkivportenDokument(
    reference: UUID,
    virksomhetsnummer: Virksomhetsnummer,
    file: ByteArray,
    varseltype: MotedeltakerVarselType,
    arbeidstakerPersonIdent: PersonIdent,
    arbeidstakernavn: String,
    hasNarmesteLeder: Boolean,
) : ArkivportenDocument {
    return ArkivportenDocument(
        documentId = reference,
        type = ArkivportenDocument.DocumentType.DIALOGMOTE,
        content = file,
        contentType = ArkivportenDocument.ContentType.PDF,
        orgnumber = virksomhetsnummer.value,
        dialogTitle = toDialogTitle(varseltype, arbeidstakerPersonIdent, arbeidstakernavn, hasNarmesteLeder),
        dialogSummary = toDialogSummary(varseltype, hasNarmesteLeder)
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

private fun toDialogTitle(
    varseltype: MotedeltakerVarselType,
    personIdent: PersonIdent,
    navn: String?,
    isCopy: Boolean,
): String {
    return "${toVarselTypeTitle(varseltype, isCopy)} - $navn (${personIdent.value})"
}

private fun toDialogSummary(varseltype: MotedeltakerVarselType, isCopy: Boolean): String {
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
