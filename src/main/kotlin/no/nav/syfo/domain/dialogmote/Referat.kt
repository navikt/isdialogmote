package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.ArbeidstakerBrevDTO
import no.nav.syfo.domain.BrevType
import no.nav.syfo.domain.NarmesteLederBrevDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.infrastructure.client.dokarkiv.domain.createJournalpostRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class Referat(
    val id: Int,
    override val uuid: UUID,
    override val createdAt: LocalDateTime,
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

fun List<Referat>.ferdigstilte(): List<Referat> {
    return this.filter { it.ferdigstilt }
}

fun Referat.toJournalforingRequestArbeidstaker(
    personIdent: PersonIdent,
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
    varselUuid = UUID.nameUUIDFromBytes((this.uuid.toString() + "arbeidstaker").toByteArray()),
    kanal = JournalpostKanal.DITT_NAV,
)

fun Referat.toJournalforingRequestArbeidsgiver(
    brukerPersonIdent: PersonIdent,
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
    varselUuid = UUID.nameUUIDFromBytes((this.uuid.toString() + "arbeidsgiver").toByteArray()),
    kanal = JournalpostKanal.DITT_NAV,
)

fun Referat.toJournalforingRequestBehandler(
    brukerPersonIdent: PersonIdent,
    behandlerPersonIdent: PersonIdent?,
    behandlerHprId: Int?,
    behandlerNavn: String,
    pdf: ByteArray,
    moteTidspunkt: LocalDateTime?,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerPersonIdent = behandlerPersonIdent,
    mottakerHprId = behandlerHprId,
    mottakerNavn = behandlerNavn,
    digitalt = this.digitalt,
    dokumentName = this.toJournalpostTittel(moteTidspunkt),
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_BEH,
    dokumentPdf = pdf,
    kanal = JournalpostKanal.HELSENETTET,
    varselUuid = UUID.nameUUIDFromBytes((this.uuid.toString() + "behandler").toByteArray())
)

fun Referat.toJournalpostTittel(
    moteTidspunkt: LocalDateTime?,
): String {
    val dateFormatterNorwegian = DateTimeFormatter.ofPattern("d. MMMM YYYY", Locale.forLanguageTag("no-NO"))
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
