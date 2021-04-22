package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidstakerVarselDTO
import no.nav.syfo.varsel.MotedeltakerVarselType
import java.time.LocalDateTime
import java.util.UUID

data class DialogmotedeltakerArbeidstakerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerArbeidstakerId: Int,
    val varselType: MotedeltakerVarselType,
    val digitalt: Boolean,
    val pdf: ByteArray,
    val status: String,
    val lestDato: LocalDateTime?,
    val fritekst: String
)

fun DialogmotedeltakerArbeidstakerVarsel.toDialogmotedeltakerArbeidstakerVarselDTO() =
    DialogmotedeltakerArbeidstakerVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        digitalt = this.digitalt,
        pdf = this.pdf,
        lestDato = this.lestDato,
        fritekst = this.fritekst,
    )
