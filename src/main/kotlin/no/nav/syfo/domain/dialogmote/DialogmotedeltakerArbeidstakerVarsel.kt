package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.DialogmotedeltakerArbeidstakerVarselDTO
import no.nav.syfo.api.dto.DialogmotedeltakerArbeidstakerVarselSvarDTO
import no.nav.syfo.infrastructure.client.dokarkiv.domain.createJournalpostRequest
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.ArbeidstakerBrevDTO
import no.nav.syfo.domain.ArbeidstakerBrevSvarDTO
import no.nav.syfo.infrastructure.client.dokarkiv.domain.DialogmoteDeltakerType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostKanal
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidstakerVarsel(
    val id: Int,
    override val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    override val motedeltakerArbeidstakerId: Int,
    val varselType: MotedeltakerVarselType,
    val digitalt: Boolean,
    override val pdfId: Int,
    val status: String,
    override val lestDatoArbeidstaker: LocalDateTime?,
    val fritekst: String,
    override val document: List<DocumentComponentDTO>,
    val journalpostId: String?,
    val brevBestillingsId: String?,
    val brevBestiltTidspunkt: LocalDateTime?,
    val svarType: DialogmoteSvarType?,
    val svarTekst: String?,
    val svarTidspunkt: LocalDateTime?,
) : ArbeidstakerBrev

fun DialogmotedeltakerArbeidstakerVarsel.toDialogmotedeltakerArbeidstakerVarselDTO() =
    DialogmotedeltakerArbeidstakerVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        digitalt = this.digitalt,
        lestDato = this.lestDatoArbeidstaker,
        document = this.document,
        brevBestiltTidspunkt = this.brevBestiltTidspunkt,
        fritekst = this.fritekst,
        svar = this.svarType?.let {
            DialogmotedeltakerArbeidstakerVarselSvarDTO(
                svarTidspunkt = this.svarTidspunkt!!,
                svarType = it.name,
                svarTekst = this.svarTekst,
            )
        },
    )

fun DialogmotedeltakerArbeidstakerVarsel.toArbeidstakerBrevDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = ArbeidstakerBrevDTO(
    uuid = this.uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = this.createdAt,
    brevType = this.varselType.toBrevType().name,
    digitalt = this.digitalt,
    lestDato = this.lestDatoArbeidstaker,
    fritekst = this.fritekst,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
    svar = this.svarType?.let {
        ArbeidstakerBrevSvarDTO(
            svarType = it.name,
            svarTekst = this.svarTekst,
            svarTidspunkt = this.svarTidspunkt!!,
        )
    },
)

fun DialogmotedeltakerArbeidstakerVarsel.toJournalpostRequest(
    personIdent: PersonIdent,
    navn: String,
    pdf: ByteArray,
) = createJournalpostRequest(
    brukerPersonIdent = personIdent,
    mottakerPersonIdent = personIdent,
    mottakerNavn = navn,
    digitalt = this.digitalt,
    dokumentName = this.varselType.toJournalpostTittel(),
    brevkodeType = this.varselType.toBrevkodeType(DialogmoteDeltakerType.ARBEIDSTAKER),
    dokumentPdf = pdf,
    varselUuid = this.uuid,
    kanal = JournalpostKanal.DITT_NAV,
)
