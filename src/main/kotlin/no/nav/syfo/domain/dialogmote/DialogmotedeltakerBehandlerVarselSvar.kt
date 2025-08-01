package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.DialogmotedeltakerBehandlerVarselSvarDTO
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerBehandlerVarselSvar(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val type: DialogmoteSvarType,
    val tekst: String,
    val msgId: String,
)

fun DialogmotedeltakerBehandlerVarselSvar.toDialogmotedeltakerBehandlerVarselSvarDTO() = DialogmotedeltakerBehandlerVarselSvarDTO(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    svarType = this.type.name,
    tekst = this.tekst,
)
