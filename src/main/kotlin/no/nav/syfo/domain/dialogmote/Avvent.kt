package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Avvent (
    val uuid: UUID,
    val motebehovUuid: UUID,
    val createdAt: OffsetDateTime,
    val frist: LocalDate,
    val createdBy: String,
    val personident: PersonIdent,
    val beskrivelse: String,
    val isLukket: Boolean,
)
