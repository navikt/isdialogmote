package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime

data class DialogmoteDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: String,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiverDTO,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val referat: ReferatDTO?,
)
