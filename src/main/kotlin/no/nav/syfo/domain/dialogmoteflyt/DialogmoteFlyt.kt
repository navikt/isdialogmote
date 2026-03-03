package no.nav.syfo.domain.dialogmoteflyt

import java.time.LocalDateTime
import java.util.*

data class DialogmoteFlyt(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val endringer: List<DialogmoteFlytEndring>,
)
