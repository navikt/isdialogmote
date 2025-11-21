package no.nav.syfo.infrastructure.client.arkivporten

import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType

data class ArkivportenDocument(
    val documentId: UUID,
    val type: DocumentType,
    val content: ByteArray,
    val contentType: ContentType,
    val orgNumber: String,
    val title: String,
    val summary: String,
    val fnr: String,
    val fullName: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArkivportenDocument

        if (type != other.type) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false
        if (orgNumber != other.orgNumber) return false
        if (title != other.title) return false
        if (summary != other.summary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + orgNumber.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + fnr.hashCode()
        result = 31 * result + fullName.hashCode()
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
): ArkivportenDocument {
    return ArkivportenDocument(
        documentId = reference,
        type = ArkivportenDocument.DocumentType.DIALOGMOTE,
        content = file,
        contentType = ArkivportenDocument.ContentType.PDF,
        orgNumber = virksomhetsnummer.value,
        title = title(varseltype, arbeidstakernavn),
        summary = summary(varseltype, arbeidstakernavn),
        fnr = arbeidstakerPersonIdent.value,
        fullName = arbeidstakernavn,
    )
}

fun MotedeltakerVarselType.toDescription(): String {
    return when (this) {
        MotedeltakerVarselType.INNKALT -> "innkalling til dialogmøte"
        MotedeltakerVarselType.NYTT_TID_STED -> "endring av dialogmøte"
        MotedeltakerVarselType.AVLYST -> "avlysning av dialogmøte"
        MotedeltakerVarselType.REFERAT -> "referat fra dialogmøte"
    }
}

private fun summary(type: MotedeltakerVarselType, navn: String): String {
    return "Nav har sendt ${type.toDescription()} til arbeidsgiver angående arbeidstaker $navn"
}

private fun title(type: MotedeltakerVarselType, navn: String): String {
    return "${type.toDescription().capitalizeFirstLetter()} til arbeidsgiveren angående $navn. Hvis nærmeste leder er meldt inn til Nav, mottar lederen ${type.toDescription()} på \"Dine sykmeldte\" hos Nav."
}

fun String.capitalizeFirstLetter(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
