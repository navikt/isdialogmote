package no.nav.syfo.api.dto

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreateAvventDTO(
    val motebehovUuid: UUID,
    val frist: LocalDate,
    val createdBy: String,
    val personident: PersonIdent,
    val beskrivelse: String,
)

fun CreateAvventDTO.toAvvent(): Avvent {
    return Avvent(
        uuid = UUID.randomUUID(),
        motebehovUuid = this.motebehovUuid,
        createdAt = OffsetDateTime.now(),
        frist = this.frist,
        createdBy = this.createdBy,
        personident = this.personident,
        beskrivelse = this.beskrivelse,
        isLukket = false
    )
}
