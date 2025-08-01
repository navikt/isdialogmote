package no.nav.syfo.api.dto

data class DialogmotedeltakerArbeidsgiverDTO(
    val uuid: String,
    val virksomhetsnummer: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarselDTO>,
)
