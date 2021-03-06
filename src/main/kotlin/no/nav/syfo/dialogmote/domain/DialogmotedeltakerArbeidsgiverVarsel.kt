package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidsgiverVarselDTO
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidsgiverVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerArbeidsgiverId: Int,
    val varselType: MotedeltakerVarselType,
    val pdf: ByteArray,
    val status: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
)

fun DialogmotedeltakerArbeidsgiverVarsel.toDialogmotedeltakerArbeidsgiverVarselDTO() =
    DialogmotedeltakerArbeidsgiverVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        lestDato = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
    )
