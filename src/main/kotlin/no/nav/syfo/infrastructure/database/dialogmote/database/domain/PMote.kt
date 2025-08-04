package no.nav.syfo.infrastructure.database.dialogmote.database.domain

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.Referat
import java.time.LocalDateTime
import java.util.*

data class PDialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: String,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
)

fun PDialogmote.toDialogmote(
    dialogmotedeltakerArbeidstaker: DialogmotedeltakerArbeidstaker,
    dialogmotedeltakerArbeidsgiver: DialogmotedeltakerArbeidsgiver,
    dialogmotedeltakerBehandler: DialogmotedeltakerBehandler?,
    dialogmoteTidStedList: List<DialogmoteTidSted>,
    referatList: List<Referat>,
): Dialogmote =
    Dialogmote(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        status = DialogmoteStatus.valueOf(this.status),
        opprettetAv = this.opprettetAv,
        tildeltVeilederIdent = this.tildeltVeilederIdent,
        tildeltEnhet = this.tildeltEnhet,
        arbeidstaker = dialogmotedeltakerArbeidstaker,
        arbeidsgiver = dialogmotedeltakerArbeidsgiver,
        behandler = dialogmotedeltakerBehandler,
        tidStedList = dialogmoteTidStedList,
        referatList = referatList,
    )
