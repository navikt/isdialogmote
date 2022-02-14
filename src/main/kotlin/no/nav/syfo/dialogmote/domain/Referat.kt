package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerAnnenDTO
import no.nav.syfo.dialogmote.api.domain.ReferatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.UUID

data class Referat(
    val id: Int,
    override val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    override val motedeltakerArbeidstakerId: Int,
    override val motedeltakerArbeidsgiverId: Int,
    val digitalt: Boolean,
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val behandlerOppgave: String?,
    val narmesteLederNavn: String,
    override val document: List<DocumentComponentDTO>,
    override val pdfId: Int?,
    val journalpostIdArbeidstaker: String?,
    override val lestDatoArbeidstaker: LocalDateTime?,
    override val lestDatoArbeidsgiver: LocalDateTime?,
    val andreDeltakere: List<DialogmotedeltakerAnnen>,
    val brevBestillingsId: String?,
    val brevBestiltTidspunkt: LocalDateTime?,
    val ferdigstilt: Boolean,
) : ArbeidstakerBrev, NarmesteLederBrev

data class DialogmotedeltakerAnnen(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val funksjon: String,
    val navn: String,
)

fun Referat.toReferatDTO(): ReferatDTO {
    return ReferatDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        digitalt = this.digitalt,
        situasjon = this.situasjon,
        konklusjon = this.konklusjon,
        arbeidstakerOppgave = this.arbeidstakerOppgave,
        arbeidsgiverOppgave = this.arbeidsgiverOppgave,
        veilederOppgave = this.veilederOppgave,
        behandlerOppgave = this.behandlerOppgave,
        narmesteLederNavn = this.narmesteLederNavn, // Egentlig NL-representant fra virksomhet
        document = this.document,
        lestDatoArbeidstaker = this.lestDatoArbeidstaker,
        lestDatoArbeidsgiver = this.lestDatoArbeidsgiver,
        andreDeltakere = this.andreDeltakere.map {
            it.toDialogmotedeltakerAnnenDTO()
        },
        brevBestiltTidspunkt = this.brevBestiltTidspunkt,
        ferdigstilt = this.ferdigstilt,
    )
}

fun DialogmotedeltakerAnnen.toDialogmotedeltakerAnnenDTO(): DialogmotedeltakerAnnenDTO {
    return DialogmotedeltakerAnnenDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        funksjon = this.funksjon,
        navn = this.navn,
    )
}

fun Referat.toJournalforingRequestArbeidstaker(
    personIdent: PersonIdentNumber,
    navn: String,
    pdf: ByteArray,
) = createJournalpostRequest(
    brukerPersonIdent = personIdent,
    mottakerPersonIdent = personIdent,
    mottakerNavn = navn,
    digitalt = this.digitalt,
    dokumentName = "Referat fra dialogmøte",
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AT,
    dokumentPdf = pdf,
)

fun Referat.toJournalforingRequestArbeidsgiver(
    brukerPersonIdent: PersonIdentNumber,
    virksomhetsnummer: Virksomhetsnummer?,
    virksomhetsnavn: String,
    pdf: ByteArray,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerVirksomhetsnummer = virksomhetsnummer,
    mottakerNavn = virksomhetsnavn,
    digitalt = this.digitalt,
    dokumentName = "Referat fra dialogmøte",
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AG,
    dokumentPdf = pdf,
)

fun Referat.toJournalforingRequestBehandler(
    brukerPersonIdent: PersonIdentNumber,
    behandlerPersonIdent: PersonIdentNumber?,
    behandlerNavn: String,
    pdf: ByteArray,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerPersonIdent = behandlerPersonIdent,
    mottakerNavn = behandlerNavn,
    digitalt = this.digitalt,
    dokumentName = "Referat fra dialogmøte",
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_BEH,
    dokumentPdf = pdf,
    kanal = JournalpostKanal.HELSENETTET,
)

fun Referat.toArbeidstakerBrevDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = ArbeidstakerBrevDTO(
    uuid = uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = createdAt,
    brevType = MotedeltakerVarselType.REFERAT.name,
    digitalt = digitalt,
    lestDato = lestDatoArbeidstaker,
    fritekst = konklusjon,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = document,
    svar = null,
)

fun Referat.toNarmesteLederBrevDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = NarmesteLederBrevDTO(
    uuid = this.uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = this.createdAt,
    brevType = MotedeltakerVarselType.REFERAT.name,
    lestDato = this.lestDatoArbeidsgiver,
    fritekst = konklusjon,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
    svar = null,
)
