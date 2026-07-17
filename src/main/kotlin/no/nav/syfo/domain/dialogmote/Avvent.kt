package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.Personident
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Avvent(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personident: Personident,
    val beskrivelse: String,
    val isLukket: Boolean,
)
