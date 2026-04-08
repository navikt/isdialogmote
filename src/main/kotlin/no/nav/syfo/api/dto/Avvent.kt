package no.nav.syfo.api.dto

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class AvventDTO(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personident: String,
    val beskrivelse: String,
    val isLukket: Boolean,
)

fun Avvent.toAvventDTO(): AvventDTO =
    AvventDTO(
        uuid = this.uuid,
        createdAt = this.createdAt,
        frist = this.frist,
        createdBy = this.createdBy,
        personident = this.personident.value,
        beskrivelse = this.beskrivelse,
        isLukket = this.isLukket,
    )

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
