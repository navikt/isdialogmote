package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerArbeidsgiverVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerArbeidsgiverId: Int,
    val varselType: MotedeltakerVarselType,
    val pdfId: Int,
    val status: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svarType: String?,
    val svarTekst: String?,
    val svarTidspunkt: LocalDateTime?,
)

fun PMotedeltakerArbeidsgiverVarsel.toDialogmotedeltakerArbeidsgiver() =
    DialogmotedeltakerArbeidsgiverVarsel(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        motedeltakerArbeidsgiverId = this.motedeltakerArbeidsgiverId,
        varselType = this.varselType,
        pdfId = this.pdfId,
        status = this.status,
        lestDatoArbeidsgiver = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
        svarType = this.svarType?.let {
            DialogmoteSvarType.valueOf(this.svarType)
        },
        svarTekst = this.svarTekst,
        svarTidspunkt = this.svarTidspunkt,
    )
