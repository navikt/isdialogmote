package no.nav.syfo.api.dto

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreateAvventDTO(
    val frist: LocalDate,
    val personident: String,
    val beskrivelse: String,
) {
    fun toAvvent(createdBy: String): Avvent =
        Avvent(
            uuid = UUID.randomUUID(),
            createdAt = OffsetDateTime.now(),
            frist = this.frist,
            createdBy = createdBy,
            personident = PersonIdent(this.personident),
            beskrivelse = this.beskrivelse,
            isLukket = false,
        )
}

data class LukkAvventDTO(
    val personident: String,
)

data class QueryAvventDTO(
    val personidenter: List<String>,
)
