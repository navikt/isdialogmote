package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class PMotedeltakerArbeidsgiver(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val virksomhetsnummer: Virksomhetsnummer,
)

fun PMotedeltakerArbeidsgiver.toDialogmotedeltakerArbeidsgiver(
    dialogmotedeltakerArbeidsgiverVarsel: List<DialogmotedeltakerArbeidsgiverVarsel>,
) =
    DialogmotedeltakerArbeidsgiver(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        virksomhetsnummer = this.virksomhetsnummer,
        type = DialogmotedeltakerType.ARBEIDSGIVER,
        varselList = dialogmotedeltakerArbeidsgiverVarsel,
    )
