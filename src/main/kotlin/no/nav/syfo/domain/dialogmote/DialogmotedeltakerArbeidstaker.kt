package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDateTime
import java.util.UUID

data class DialogmotedeltakerArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val personIdent: PersonIdent,
    val type: DialogmotedeltakerType,
    val varselList: List<DialogmotedeltakerArbeidstakerVarsel>,
)
