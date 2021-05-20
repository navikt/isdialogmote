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
    )
}

fun List<Dialogmote>.toArbeidstakerVarselDTOList(): List<ArbeidstakerVarselDTO> {
    return this.map { dialogmote ->
        dialogmote.arbeidstaker.varselList.map {
            it.toArbeidstakerVarselDTO(
                dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                deltakerUuid = dialogmote.arbeidstaker.uuid,
                virksomhetsummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            )
        }
    }.flatten()
}

fun List<Dialogmote>.anyUnfinished(): Boolean {
    return this.any {
        it.status.unfinished()
    }
}
