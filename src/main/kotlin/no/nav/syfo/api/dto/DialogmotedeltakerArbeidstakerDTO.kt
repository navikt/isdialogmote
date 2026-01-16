package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker

data class DialogmotedeltakerArbeidstakerDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidstakerVarselDTO>,
) {
    companion object {
        fun from(arbeidstaker: DialogmotedeltakerArbeidstaker): DialogmotedeltakerArbeidstakerDTO {
            return DialogmotedeltakerArbeidstakerDTO(
                uuid = arbeidstaker.uuid.toString(),
                personIdent = arbeidstaker.personIdent.value,
                type = arbeidstaker.type.name,
                varselList = arbeidstaker.varselList.map { DialogmotedeltakerArbeidstakerVarselDTO.from(it) },
            )
        }
    }
}
