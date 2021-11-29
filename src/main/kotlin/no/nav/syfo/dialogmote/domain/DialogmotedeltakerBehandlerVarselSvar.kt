package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerBehandlerVarselSvarDTO
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerBehandlerVarselSvar(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val type: DialogmoteSvarType,
    val tekst: String,
)

fun DialogmotedeltakerBehandlerVarselSvar.toDialogmotedeltakerBehandlerVarselSvarDTO() = DialogmotedeltakerBehandlerVarselSvarDTO(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    svarType = this.type.name,
    tekst = this.tekst,
)
