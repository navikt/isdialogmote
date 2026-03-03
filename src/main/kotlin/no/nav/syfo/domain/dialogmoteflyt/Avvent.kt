package no.nav.syfo.domain.dialogmoteflyt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class Avvent(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val dialogmoteFlytId: Int,
    val frist: LocalDate,
    val beskrivelse: String?,
    val personident: String,
    val createdBy: String,
)
