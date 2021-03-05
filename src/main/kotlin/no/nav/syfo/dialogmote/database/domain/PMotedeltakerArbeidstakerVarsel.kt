package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.varsel.MotedeltakerVarselType
import java.time.LocalDateTime
import java.util.*

data class PMotedeltakerArbeidstakerVarsel(
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
        pdf = this.pdf,
        status = this.status,
        lestDato = this.lestDato,
    )