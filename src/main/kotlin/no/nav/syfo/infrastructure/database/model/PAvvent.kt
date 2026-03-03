package no.nav.syfo.infrastructure.database.model

import no.nav.syfo.domain.dialogmoteflyt.Avvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class PAvvent(
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

fun PAvvent.toAvvent(): Avvent =
    Avvent(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        dialogmoteFlytId = this.dialogmoteFlytId,
        frist = this.frist,
        beskrivelse = this.beskrivelse,
        personident = this.personident,
        createdBy = this.createdBy,
    )
