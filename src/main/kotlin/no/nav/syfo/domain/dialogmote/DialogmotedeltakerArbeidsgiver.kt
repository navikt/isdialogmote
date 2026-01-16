package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidsgiver(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val virksomhetsnummer: Virksomhetsnummer,
    val type: DialogmotedeltakerType,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarsel>,
)
