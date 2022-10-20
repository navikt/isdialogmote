package no.nav.syfo.dialogmote.domain

import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.util.isAfterOrEqual
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
    val referatList: List<Referat>,
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
        referatList = this.referatList.toReferatDTOList(),
    )
}

fun List<Dialogmote>.toArbeidstakerBrevDTOList(): List<ArbeidstakerBrevDTO> {
    return this.map { dialogmote ->
        val brevList = mutableListOf<ArbeidstakerBrevDTO>()
        dialogmote.referatList.ferdigstilte().map {
            brevList.add(
                it.toArbeidstakerBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            )
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
        dialogmote.referatList.ferdigstilte().map {
            brevList.add(
                it.toNarmesteLederBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidsgiver.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            )
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

fun List<Dialogmote>.removeBrevBeforeDate(date: LocalDate): List<Dialogmote> {
    return this.map {
        it.copy(
            arbeidsgiver = it.arbeidsgiver.copy(
                varselList = it.arbeidsgiver.varselList.filter {
                    it.createdAt.toLocalDate().isAfterOrEqual(date)
                }
            ),
            referatList = it.referatList.filter { it.createdAt.toLocalDate().isAfterOrEqual(date) }
        )
    }
}
