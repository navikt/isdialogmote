package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
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
    val document: List<DocumentComponentDTO>,
    val narmesteLederNavn: String,
    val pdf: ByteArray,
    val journalpostId: String?,
    val lestDatoArbeidstaker: LocalDateTime?,
    val lestDatoArbeidsgiver: LocalDateTime?,
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
): Referat =
    Referat(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
        digitalt = this.digitalt,
        situasjon = this.situasjon,
        konklusjon = this.konklusjon,
        arbeidstakerOppgave = this.arbeidstakerOppgave,
        arbeidsgiverOppgave = this.arbeidsgiverOppgave,
        veilederOppgave = this.veilederOppgave,
        narmesteLederNavn = this.narmesteLederNavn,
        document = this.document,
        pdf = this.pdf,
        journalpostId = this.journalpostId,
        lestDatoArbeidstaker = this.lestDatoArbeidstaker,
        lestDatoArbeidsgiver = this.lestDatoArbeidsgiver,
        andreDeltakere = andreDeltakere,
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
