package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime

data class DialogmoteTidStedDTO(
    val uuid: String,
    val sted: String,
    val tid: LocalDateTime,
)
