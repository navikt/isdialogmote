package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import java.time.LocalDateTime
import java.util.UUID

data class Dialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstaker,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiver,
    val behandler: DialogmotedeltakerBehandler?,
    val tidStedList: List<DialogmoteTidSted>,
    val referat: Referat?,
)

fun Dialogmote.toDialogmoteDTO(): DialogmoteDTO {
    val dialogmoteTidSted = this.tidStedList.latest()!!
    return DialogmoteDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        status = this.status.name,
        opprettetAv = this.opprettetAv,
        tildeltVeilederIdent = this.tildeltVeilederIdent,
        tildeltEnhet = this.tildeltEnhet,
        arbeidstaker = this.arbeidstaker.toDialogmotedeltakerArbeidstakerDTO(),
        arbeidsgiver = this.arbeidsgiver.toDialogmotedeltakerArbeidsgiverDTO(),
        behandler = this.behandler?.toDialogmotedeltakerBehandlerDTO(),
        sted = dialogmoteTidSted.sted,
        tid = dialogmoteTidSted.tid,
        videoLink = dialogmoteTidSted.videoLink,
        referat = this.referat?.toReferatDTO(),
    )
}

fun List<Dialogmote>.toArbeidstakerBrevDTOList(): List<ArbeidstakerBrevDTO> {
    return this.map { dialogmote ->
        val brevList = mutableListOf<ArbeidstakerBrevDTO>()
        dialogmote.referat?.let {
            if (it.ferdigstilt) {
                brevList.add(
                    it.toArbeidstakerBrevDTO(
                        dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                        deltakerUuid = dialogmote.arbeidstaker.uuid,
                        virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                    )
                )
            }
        }
        brevList.addAll(
            dialogmote.arbeidstaker.varselList.map {
                it.toArbeidstakerBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            }
        )
        brevList
    }.flatten()
}

fun List<Dialogmote>.toNarmesteLederBrevDTOList(): List<NarmesteLederBrevDTO> {
    return this.map { dialogmote ->
        val brevList = mutableListOf<NarmesteLederBrevDTO>()
        dialogmote.referat?.let {
            if (it.ferdigstilt) {
                brevList.add(
                    it.toNarmesteLederBrevDTO(
                        dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                        deltakerUuid = dialogmote.arbeidsgiver.uuid,
                        virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                    )
                )
            }
        }
        brevList.addAll(
            dialogmote.arbeidsgiver.varselList.map {
                it.toNarmesteLederBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidsgiver.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            }
        )
        brevList
    }.flatten()
}

fun List<Dialogmote>.anyUnfinished(): Boolean {
    return this.any {
        it.status.unfinished()
    }
}
