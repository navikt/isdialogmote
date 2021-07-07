package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidsgiverVarselDTO
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidsgiverVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerArbeidsgiverId: Int,
    val varselType: MotedeltakerVarselType,
    val pdf: ByteArray,
    val status: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
)

fun DialogmotedeltakerArbeidsgiverVarsel.toDialogmotedeltakerArbeidsgiverVarselDTO() =
    DialogmotedeltakerArbeidsgiverVarselDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        varselType = this.varselType.name,
        lestDato = this.lestDato,
        fritekst = this.fritekst,
        document = this.document,
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
    lestDato = this.lestDato,
    fritekst = this.fritekst,
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
)
