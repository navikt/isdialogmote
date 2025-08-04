package no.nav.syfo.api.dto

data class DialogmotedeltakerBehandlerDTO(
    val uuid: String,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val behandlerType: String,
    val type: String,
    val personIdent: String?,
    val varselList: List<DialogmotedeltakerBehandlerVarselDTO>,
    val deltatt: Boolean,
    val mottarReferat: Boolean
)
