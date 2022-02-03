package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.DialogmotedeltakerAnnen
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.Referat
import java.time.LocalDateTime
import java.util.UUID

data class PReferat(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val digitalt: Boolean,
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val behandlerOppgave: String?,
    val document: List<DocumentComponentDTO>,
    val narmesteLederNavn: String,
    val pdfId: Int,
    val journalpostIdArbeidstaker: String?,
    val lestDatoArbeidstaker: LocalDateTime?,
    val lestDatoArbeidsgiver: LocalDateTime?,
    val brevBestillingsId: String?,
    val brevBestiltTidspunkt: LocalDateTime?,
    val ferdigstilt: Boolean,
)

data class PMotedeltakerAnnen(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteReferatId: Int,
    val funksjon: String,
    val navn: String,
)

fun PReferat.toReferat(
    andreDeltakere: List<DialogmotedeltakerAnnen>,
    motedeltakerArbeidstakerId: Int,
    motedeltakerArbeidsgiverId: Int,
): Referat =
    Referat(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
        motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId,
        digitalt = this.digitalt,
        situasjon = this.situasjon,
        konklusjon = this.konklusjon,
        arbeidstakerOppgave = this.arbeidstakerOppgave,
        arbeidsgiverOppgave = this.arbeidsgiverOppgave,
        veilederOppgave = this.veilederOppgave,
        behandlerOppgave = this.behandlerOppgave,
        narmesteLederNavn = this.narmesteLederNavn,
        document = this.document,
        pdfId = this.pdfId,
        journalpostIdArbeidstaker = this.journalpostIdArbeidstaker,
        lestDatoArbeidstaker = this.lestDatoArbeidstaker,
        lestDatoArbeidsgiver = this.lestDatoArbeidsgiver,
        andreDeltakere = andreDeltakere,
        brevBestillingsId = this.brevBestillingsId,
        brevBestiltTidspunkt = this.brevBestiltTidspunkt,
        ferdigstilt = this.ferdigstilt,
    )

fun PMotedeltakerAnnen.toDialogmoteDeltakerAnnen(): DialogmotedeltakerAnnen =
    DialogmotedeltakerAnnen(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        funksjon = this.funksjon,
        navn = this.navn,
    )
