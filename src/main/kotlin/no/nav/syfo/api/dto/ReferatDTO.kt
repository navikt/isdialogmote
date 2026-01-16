package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.Referat
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
) {
    companion object {
        fun from(referat: Referat): ReferatDTO {
            return ReferatDTO(
                uuid = referat.uuid.toString(),
                createdAt = referat.createdAt,
                updatedAt = referat.updatedAt,
                digitalt = referat.digitalt,
                situasjon = referat.situasjon,
                konklusjon = referat.konklusjon,
                arbeidstakerOppgave = referat.arbeidstakerOppgave,
                arbeidsgiverOppgave = referat.arbeidsgiverOppgave,
                veilederOppgave = referat.veilederOppgave,
                behandlerOppgave = referat.behandlerOppgave,
                narmesteLederNavn = referat.narmesteLederNavn,
                document = referat.document,
                lestDatoArbeidstaker = referat.lestDatoArbeidstaker,
                lestDatoArbeidsgiver = referat.lestDatoArbeidsgiver,
                andreDeltakere = referat.andreDeltakere.map {
                    DialogmotedeltakerAnnenDTO.from(it)
                },
                brevBestiltTidspunkt = referat.brevBestiltTidspunkt,
                ferdigstilt = referat.ferdigstilt,
                begrunnelseEndring = referat.begrunnelseEndring,
            )
        }
    }
}

data class DialogmotedeltakerAnnenDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val funksjon: String,
    val navn: String,
) {
    companion object {
        fun from(deltaker: no.nav.syfo.domain.dialogmote.DialogmotedeltakerAnnen): DialogmotedeltakerAnnenDTO {
            return DialogmotedeltakerAnnenDTO(
                uuid = deltaker.uuid.toString(),
                createdAt = deltaker.createdAt,
                updatedAt = deltaker.updatedAt,
                funksjon = deltaker.funksjon,
                navn = deltaker.navn,
            )
        }
    }
}
