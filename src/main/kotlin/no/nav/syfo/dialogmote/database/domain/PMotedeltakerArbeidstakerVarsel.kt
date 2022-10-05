package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

data class PMotedeltakerArbeidstakerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerArbeidstakerId: Int,
    val varselType: MotedeltakerVarselType,
    val digitalt: Boolean,
    val pdfId: Int,
    val status: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val journalpostId: String?,
    val brevBestillingsId: String?,
    val brevBestiltTidspunkt: LocalDateTime?,
    val svarType: String?,
    val svarTekst: String?,
    val svarTidspunkt: LocalDateTime?,
    val svarPublishedToKafkaAt: OffsetDateTime?,
)

fun PMotedeltakerArbeidstakerVarsel.toDialogmotedeltakerArbeidstaker() =
    DialogmotedeltakerArbeidstakerVarsel(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        motedeltakerArbeidstakerId = this.motedeltakerArbeidstakerId,
        varselType = this.varselType,
        digitalt = this.digitalt,
        pdfId = this.pdfId,
        status = this.status,
        lestDatoArbeidstaker = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
        journalpostId = this.journalpostId,
        brevBestillingsId = this.brevBestillingsId,
        brevBestiltTidspunkt = this.brevBestiltTidspunkt,
        svarType = this.svarType?.let {
            DialogmoteSvarType.valueOf(this.svarType)
        },
        svarTekst = this.svarTekst,
        svarTidspunkt = this.svarTidspunkt,
    )
