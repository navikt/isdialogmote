package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class ReferatDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val situasjon: String?,
    val konklusjon: String?,
    val arbeidstakerOppgave: String?,
    val arbeidsgiverOppgave: String?,
    val veilederOppgave: String?,
    val document: List<DocumentComponentDTO>,
    val pdf: ByteArray,
    val andreDeltakere: List<DialogmotedeltakerAnnenDTO>,
)

data class DialogmotedeltakerAnnenDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val funksjon: String,
    val navn: String,
)
