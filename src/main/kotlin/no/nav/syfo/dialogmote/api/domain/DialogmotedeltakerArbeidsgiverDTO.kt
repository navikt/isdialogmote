package no.nav.syfo.dialogmote.api.domain

data class DialogmotedeltakerArbeidsgiverDTO(
    val uuid: String,
    val virksomhetsnummer: String,
//    val lederNavn: String?,
//    val lederEpost: String?,
    val type: String,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarselDTO>,
)
