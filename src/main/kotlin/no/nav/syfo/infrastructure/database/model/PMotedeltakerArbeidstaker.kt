package no.nav.syfo.infrastructure.database.model

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerType
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val personIdent: PersonIdent,
)

fun PMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(
    dialogmotedeltakerArbeidstakerVarsel: List<PMotedeltakerArbeidstakerVarsel>,
) = DialogmotedeltakerArbeidstaker(
    id = this.id,
    uuid = this.uuid,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    moteId = this.moteId,
    personIdent = this.personIdent,
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    varselList = dialogmotedeltakerArbeidstakerVarsel.map {
        it.toDialogmotedeltakerArbeidstakerVarsel()
    },
)

fun PMotedeltakerArbeidstaker.toMotedeltakerArbeidstakerUsingDomainVarsler(
    dialogmotedeltakerArbeidstakerVarsel: List<DialogmotedeltakerArbeidstakerVarsel>,
) = DialogmotedeltakerArbeidstaker(
    id = this.id,
    uuid = this.uuid,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    moteId = this.moteId,
    personIdent = this.personIdent,
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    varselList = dialogmotedeltakerArbeidstakerVarsel,
)
