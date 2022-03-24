package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.brev.domain.BrevType
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerAnnenDTO
import no.nav.syfo.dialogmote.api.domain.ReferatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val begrunnelseEndring: String?,
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
        begrunnelseEndring = this.begrunnelseEndring,
    )
}

fun List<Referat>.toReferatDTOList(): List<ReferatDTO> {
    return this.map { it.toReferatDTO() }
}

fun List<Referat>.ferdigstilte(): List<Referat> {
    return this.filter { it.ferdigstilt }
}

fun List<Referat>.latestFerdigstilt(): Referat? {
    return this.ferdigstilte().firstOrNull()
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
    moteTidspunkt: LocalDateTime?,
) = createJournalpostRequest(
    brukerPersonIdent = personIdent,
    mottakerPersonIdent = personIdent,
    mottakerNavn = navn,
    digitalt = this.digitalt,
    dokumentName = this.toJournalpostTittel(moteTidspunkt),
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AT,
    dokumentPdf = pdf,
)

fun Referat.toJournalforingRequestArbeidsgiver(
    brukerPersonIdent: PersonIdentNumber,
    virksomhetsnummer: Virksomhetsnummer?,
    virksomhetsnavn: String,
    pdf: ByteArray,
    moteTidspunkt: LocalDateTime?,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerVirksomhetsnummer = virksomhetsnummer,
    mottakerNavn = virksomhetsnavn,
    digitalt = this.digitalt,
    dokumentName = this.toJournalpostTittel(moteTidspunkt),
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AG,
    dokumentPdf = pdf,
)

fun Referat.toJournalforingRequestBehandler(
    brukerPersonIdent: PersonIdentNumber,
    behandlerPersonIdent: PersonIdentNumber?,
    behandlerNavn: String,
    pdf: ByteArray,
    moteTidspunkt: LocalDateTime?,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerPersonIdent = behandlerPersonIdent,
    mottakerNavn = behandlerNavn,
    digitalt = this.digitalt,
    dokumentName = this.toJournalpostTittel(moteTidspunkt),
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_BEH,
    dokumentPdf = pdf,
    kanal = JournalpostKanal.HELSENETTET,
)

private val dateFormatterNorwegian = DateTimeFormatter.ofPattern("d. MMMM YYYY", Locale.forLanguageTag("no-NO"))

fun Referat.toJournalpostTittel(
    moteTidspunkt: LocalDateTime?,
): String {
    val moteDatoString = moteTidspunkt?.toLocalDate()?.format(dateFormatterNorwegian) ?: ""
    val updatedDatoString = updatedAt.toLocalDate().format(dateFormatterNorwegian)
    val endretString = if (this.begrunnelseEndring == null) "" else " - Endret $updatedDatoString"
    return "${MotedeltakerVarselType.REFERAT.toJournalpostTittel()} $moteDatoString$endretString"
}

fun Referat.toArbeidstakerBrevDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = ArbeidstakerBrevDTO(
    uuid = uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = updatedAt,
    brevType = this.getBrevType().name,
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
    createdAt = this.updatedAt,
    brevType = this.getBrevType().name,
    lestDato = this.lestDatoArbeidsgiver,
    fritekst = konklusjon,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
    svar = null,
)

private fun Referat.getBrevType() =
    if (begrunnelseEndring == null) BrevType.REFERAT else BrevType.REFERAT_ENDRET
