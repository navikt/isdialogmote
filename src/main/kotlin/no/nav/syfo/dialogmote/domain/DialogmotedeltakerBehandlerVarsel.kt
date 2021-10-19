package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerBehandlerVarselDTO
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerBehandlerVarsel(
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

fun DialogmotedeltakerBehandlerVarsel.toDialogmotedeltakerBehandlerVarselDTO() =
    DialogmotedeltakerBehandlerVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        document = this.document,
    )
