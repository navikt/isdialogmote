package no.nav.syfo.infrastructure.database.model

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
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
    dialogmotedeltakerBehandlerVarselSvar: List<PMotedeltakerBehandlerVarselSvar>,
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
        svar = dialogmotedeltakerBehandlerVarselSvar.map { it.toDialogmotedeltakerBehandlerVarselSvar() },
    )
