package no.nav.syfo.api.dto

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreateAvventDTO(
    val frist: LocalDate,
    val createdBy: String,
    val personident: String,
    val beskrivelse: String,
)

fun CreateAvventDTO.toAvvent(): Avvent {
    return Avvent(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        frist = this.frist,
        createdBy = this.createdBy,
        personident = PersonIdent(this.personident),
        beskrivelse = this.beskrivelse,
        isLukket = false
    )
}
