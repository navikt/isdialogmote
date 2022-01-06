package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevSvarDTO
import no.nav.syfo.client.dokarkiv.domain.DialogmoteDeltakerType
import no.nav.syfo.client.dokarkiv.domain.createJournalpostRequest
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.UUID

data class DialogmotedeltakerArbeidsgiverVarsel(
    val id: Int,
    override val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    override val motedeltakerArbeidsgiverId: Int,
    val varselType: MotedeltakerVarselType,
    override val pdf: ByteArray,
    val status: String,
    override val lestDatoArbeidsgiver: LocalDateTime?,
    val fritekst: String,
    override val document: List<DocumentComponentDTO>,
    val svarType: DialogmoteSvarType?,
    val svarTekst: String?,
    val svarTidspunkt: LocalDateTime?,
) : NarmesteLederBrev

fun DialogmotedeltakerArbeidsgiverVarsel.toDialogmotedeltakerArbeidsgiverVarselDTO() =
    DialogmotedeltakerArbeidsgiverVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        lestDato = this.lestDatoArbeidsgiver,
        fritekst = this.fritekst,
        document = this.document,
        svar = this.svarType?.let {
            DialogmotedeltakerArbeidsgiverVarselSvarDTO(
                svarTidspunkt = this.svarTidspunkt!!,
                svarType = it.name,
                svarTekst = this.svarTekst,
            )
        },
    )

fun DialogmotedeltakerArbeidsgiverVarsel.toNarmesteLederBrevDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = NarmesteLederBrevDTO(
    uuid = this.uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = this.createdAt,
    brevType = this.varselType.name,
    lestDato = this.lestDatoArbeidsgiver,
    fritekst = this.fritekst,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
    svar = this.svarType?.let {
        NarmesteLederBrevSvarDTO(
            svarType = it.name,
            svarTekst = this.svarTekst,
            svarTidspunkt = this.svarTidspunkt!!,
        )
    },
)

fun DialogmotedeltakerArbeidsgiverVarsel.toJournalpostRequest(
    brukerPersonIdent: PersonIdentNumber,
    virksomhetsnummer: Virksomhetsnummer?,
    virksomhetsnavn: String,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerVirksomhetsnummer = virksomhetsnummer,
    mottakerNavn = virksomhetsnavn,
    digitalt = true,
    dokumentName = this.varselType.toJournalpostTittel(),
    brevkodeType = this.varselType.toBrevkodeType(DialogmoteDeltakerType.ARBEIDSGIVER),
    dokumentPdf = this.pdf,
)
