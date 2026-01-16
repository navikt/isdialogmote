package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.latest
import java.time.LocalDateTime

data class DialogmoteDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: String,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiverDTO,
    val behandler: DialogmotedeltakerBehandlerDTO?,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val referatList: List<ReferatDTO>,
) {
    companion object {
        fun from(dialogmote: Dialogmote): DialogmoteDTO {
            val dialogmoteTidSted = dialogmote.tidStedList.latest()!!
            return DialogmoteDTO(
                uuid = dialogmote.uuid.toString(),
                createdAt = dialogmote.createdAt,
                updatedAt = dialogmote.updatedAt,
                status = dialogmote.status.name,
                opprettetAv = dialogmote.opprettetAv,
                tildeltVeilederIdent = dialogmote.tildeltVeilederIdent,
                tildeltEnhet = dialogmote.tildeltEnhet,
                arbeidstaker = DialogmotedeltakerArbeidstakerDTO.from(dialogmote.arbeidstaker),
                arbeidsgiver = DialogmotedeltakerArbeidsgiverDTO.from(dialogmote.arbeidsgiver),
                behandler = dialogmote.behandler?.let { DialogmotedeltakerBehandlerDTO.from(it) },
                sted = dialogmoteTidSted.sted,
                tid = dialogmoteTidSted.tid,
                videoLink = dialogmoteTidSted.videoLink,
                referatList = dialogmote.referatList.map { referat -> ReferatDTO.from(referat) },
            )
        }
    }
}
