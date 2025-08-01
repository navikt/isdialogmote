package no.nav.syfo.api.dto

data class DialogmotedeltakerArbeidstakerDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidstakerVarselDTO>,
)
