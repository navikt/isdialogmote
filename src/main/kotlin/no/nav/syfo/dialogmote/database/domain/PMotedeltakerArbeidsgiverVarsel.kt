package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.varsel.MotedeltakerVarselType
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerArbeidsgiverVarsel(
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

fun PMotedeltakerArbeidsgiverVarsel.toDialogmotedeltakerArbeidsgiver() =
    DialogmotedeltakerArbeidsgiverVarsel(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        motedeltakerArbeidsgiverId = this.motedeltakerArbeidsgiverId,
        varselType = this.varselType,
        pdf = this.pdf,
        status = this.status,
        lestDato = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
    )
