package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class ReferatDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val digitalt: Boolean,
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val behandlerOppgave: String?,
    val narmesteLederNavn: String,
    val document: List<DocumentComponentDTO>,
    val lestDatoArbeidstaker: LocalDateTime?,
    val lestDatoArbeidsgiver: LocalDateTime?,
    val andreDeltakere: List<DialogmotedeltakerAnnenDTO>,
    val brevBestiltTidspunkt: LocalDateTime?,
    val ferdigstilt: Boolean,
    val begrunnelseEndring: String? = null,
)

data class DialogmotedeltakerAnnenDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val funksjon: String,
    val navn: String,
)
