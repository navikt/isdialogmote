package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import java.time.LocalDateTime
import java.util.*

data class Dialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val planlagtMoteUuid: UUID,
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstaker,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiver,
)

fun Dialogmote.toDialogmoteDTO() =
    DialogmoteDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        planlagtMoteUuid = this.planlagtMoteUuid.toString(),
        status = this.status.name,
        opprettetAv = this.opprettetAv,
        tildeltVeilederIdent = this.tildeltVeilederIdent,
        tildeltEnhet = this.tildeltEnhet,
        arbeidstaker = this.arbeidstaker.toDialogmotedeltakerArbeidstakerDTO(),
        arbeidsgiver = this.arbeidsgiver.toDialogmotedeltakerArbeidsgiverDTO(),
    )
