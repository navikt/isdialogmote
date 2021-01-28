package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime
import java.util.*

data class DialogmoteDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val planlagtMoteUuid: String,
    val status: String,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiverDTO,
)
