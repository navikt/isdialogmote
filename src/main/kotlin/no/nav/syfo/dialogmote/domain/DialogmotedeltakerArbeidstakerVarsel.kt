package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.dokarkiv.domain.createJournalpostRequest
import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidstakerVarselDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.varsel.*
import no.nav.syfo.varsel.arbeidstaker.domain.ArbeidstakerVarselDTO
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
    override val pdf: ByteArray,
    val status: String,
    override val lestDatoArbeidstaker: LocalDateTime?,
    val fritekst: String,
    override val document: List<DocumentComponentDTO>,
) : ArbeidstakerBrev

fun DialogmotedeltakerArbeidstakerVarsel.toDialogmotedeltakerArbeidstakerVarselDTO() =
    DialogmotedeltakerArbeidstakerVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        digitalt = this.digitalt,
        lestDato = this.lestDatoArbeidstaker,
        fritekst = this.fritekst,
        document = this.document,
    )

fun DialogmotedeltakerArbeidstakerVarsel.toArbeidstakerVarselDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = ArbeidstakerVarselDTO(
    uuid = this.uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = this.createdAt,
    varselType = this.varselType.name,
    digitalt = this.digitalt,
    lestDato = this.lestDatoArbeidstaker,
    fritekst = this.fritekst,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
)

fun DialogmotedeltakerArbeidstakerVarsel.toJournalpostRequest(
    personIdent: PersonIdentNumber,
) = createJournalpostRequest(
    personIdent = personIdent,
    digitalt = this.digitalt,
    dokumentName = this.varselType.toJournalpostTittel(),
    brevkodeType = this.varselType.toBrevkodeType(),
    dokumentPdf = this.pdf,
)
