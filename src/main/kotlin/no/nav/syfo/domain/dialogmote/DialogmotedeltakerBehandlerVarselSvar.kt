package no.nav.syfo.domain.dialogmote

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
