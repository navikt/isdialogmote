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
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: List<DialogmotedeltakerBehandlerVarselSvar>,
)

fun DialogmotedeltakerBehandlerVarsel.toDialogmotedeltakerBehandlerVarselDTO() =
    DialogmotedeltakerBehandlerVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        document = this.document,
        fritekst = this.fritekst,
        svar = this.svar.map {
            it.toDialogmotedeltakerBehandlerVarselSvarDTO()
        }
    )
