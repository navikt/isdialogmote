package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.varsel.arbeidstaker.domain.ArbeidstakerVarselDTO
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
        sted = dialogmoteTidSted.sted,
        tid = dialogmoteTidSted.tid,
        videoLink = dialogmoteTidSted.videoLink,
        referat = this.referat?.toReferatDTO(),
    )
}

fun List<Dialogmote>.toArbeidstakerVarselDTOList(): List<ArbeidstakerVarselDTO> {
    return this.map { dialogmote ->
        val varselList = mutableListOf<ArbeidstakerVarselDTO>()
        dialogmote.referat?.let {
            varselList.add(
                it.toArbeidstakerVarselDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            )
        }
        varselList.addAll(
            dialogmote.arbeidstaker.varselList.map {
                it.toArbeidstakerVarselDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            }
        )
        varselList
    }.flatten()
}

fun List<Dialogmote>.anyUnfinished(): Boolean {
    return this.any {
        it.status.unfinished()
    }
}
