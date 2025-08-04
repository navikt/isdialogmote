package no.nav.syfo.api.dto

import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselSvarDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val svarType: String,
    val tekst: String,
)
