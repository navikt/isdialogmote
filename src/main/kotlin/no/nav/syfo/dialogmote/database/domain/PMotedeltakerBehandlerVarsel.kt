package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.util.*

data class PMotedeltakerBehandlerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerBehandlerId: Int,
    val varselType: MotedeltakerVarselType,
    val pdfId: Int,
    val status: String,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
)

fun PMotedeltakerBehandlerVarsel.toDialogmotedeltakerBehandlerVarsel(
    dialogmotedeltakerBehandlerVarselSvar: List<DialogmotedeltakerBehandlerVarselSvar>
) =
    DialogmotedeltakerBehandlerVarsel(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        motedeltakerBehandlerId = this.motedeltakerBehandlerId,
        varselType = this.varselType,
        pdfId = this.pdfId,
        status = this.status,
        fritekst = this.fritekst,
        document = this.document,
        svar = dialogmotedeltakerBehandlerVarselSvar,
    )
