package no.nav.syfo.domain.dialogmote

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class DialogmoteStatusEndret(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val motedeltakerBehandler: Boolean,
    val status: Dialogmote.Status,
    val opprettetAv: String,
    val tilfelleStart: LocalDate,
    val publishedAt: LocalDateTime?,
)
