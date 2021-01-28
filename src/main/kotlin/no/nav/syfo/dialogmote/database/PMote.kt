package no.nav.syfo.dialogmote.database

import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime
import java.util.*

data class PDialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val planlagtMoteUuid: UUID,
    val status: String,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
)

fun PDialogmote.toDialogmote(
    dialogmotedeltakerArbeidstaker: DialogmotedeltakerArbeidstaker,
    dialogmotedeltakerArbeidsgiver: DialogmotedeltakerArbeidsgiver,
): Dialogmote =
    Dialogmote(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        planlagtMoteUuid = this.planlagtMoteUuid,
        status = DialogmoteStatus.valueOf(this.status),
        opprettetAv = this.opprettetAv,
        tildeltVeilederIdent = this.tildeltVeilederIdent,
        tildeltEnhet = this.tildeltEnhet,
        arbeidstaker = dialogmotedeltakerArbeidstaker,
        arbeidsgiver = dialogmotedeltakerArbeidsgiver,
    )
