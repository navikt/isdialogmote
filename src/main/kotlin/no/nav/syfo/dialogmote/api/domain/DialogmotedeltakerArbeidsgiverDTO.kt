package no.nav.syfo.dialogmote.api.domain

data class DialogmotedeltakerArbeidsgiverDTO(
    val uuid: String,
    val virksomhetsnummer: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarselDTO>,
)
