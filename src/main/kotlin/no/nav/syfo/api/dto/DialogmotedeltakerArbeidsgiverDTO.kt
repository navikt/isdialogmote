package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver

data class DialogmotedeltakerArbeidsgiverDTO(
    val uuid: String,
    val virksomhetsnummer: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarselDTO>,
) {
    companion object {
        fun from(arbeidsgiver: DialogmotedeltakerArbeidsgiver): DialogmotedeltakerArbeidsgiverDTO {
            return DialogmotedeltakerArbeidsgiverDTO(
                uuid = arbeidsgiver.uuid.toString(),
                virksomhetsnummer = arbeidsgiver.virksomhetsnummer.value,
                type = arbeidsgiver.type.name,
                varselList = arbeidsgiver.varselList.map { DialogmotedeltakerArbeidsgiverVarselDTO.from(it) },
            )
        }
    }
}
