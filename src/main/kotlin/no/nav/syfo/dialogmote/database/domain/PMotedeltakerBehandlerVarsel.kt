package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerBehandlerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerBehandlerId: Int,
    val varselType: MotedeltakerVarselType,
    val pdf: ByteArray,
    val status: String,
    val document: List<DocumentComponentDTO>,
)

fun PMotedeltakerBehandlerVarsel.toDialogmotedeltakerBehandler() =
    DialogmotedeltakerBehandlerVarsel(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        motedeltakerBehandlerId = this.motedeltakerBehandlerId,
        varselType = this.varselType,
        pdf = this.pdf,
        status = this.status,
        document = this.document,
    )
