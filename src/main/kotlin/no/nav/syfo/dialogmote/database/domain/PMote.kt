package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
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
    referat: Referat?,
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
        referat = referat,
    )
