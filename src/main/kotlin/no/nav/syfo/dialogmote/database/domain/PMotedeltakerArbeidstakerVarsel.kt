package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.util.UUID

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
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
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
        lestDatoArbeidstaker = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
    )
