package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselSvarDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val svarType: String,
    val tekst: String,
)
