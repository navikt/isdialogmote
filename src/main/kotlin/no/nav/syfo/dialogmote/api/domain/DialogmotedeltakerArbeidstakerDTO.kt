package no.nav.syfo.dialogmote.api.domain

data class DialogmotedeltakerArbeidstakerDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidstakerVarselDTO>,
)
