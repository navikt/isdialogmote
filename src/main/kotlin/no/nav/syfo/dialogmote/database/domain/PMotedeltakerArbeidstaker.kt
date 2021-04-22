package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val personIdent: PersonIdentNumber,
    val fritekstInnkalling: String?
)

fun PMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(
    dialogmotedeltakerArbeidstakerVarsel: List<DialogmotedeltakerArbeidstakerVarsel>
) = DialogmotedeltakerArbeidstaker(
    id = this.id,
    uuid = this.uuid,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    moteId = this.moteId,
    personIdent = this.personIdent,
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    varselList = dialogmotedeltakerArbeidstakerVarsel,
    fritekstInnkalling = this.fritekstInnkalling
)
