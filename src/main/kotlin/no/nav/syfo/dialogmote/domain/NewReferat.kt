package no.nav.syfo.dialogmote.domain

data class NewReferat(
    val moteId: Int,
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val behandlerOppgave: String?,
    val narmesteLederNavn: String,
    val document: List<DocumentComponentDTO>,
    val andreDeltakere: List<NewDialogmotedeltakerAnnen>,
    val ferdigstilt: Boolean,
)

data class NewDialogmotedeltakerAnnen(
    val funksjon: String,
    val navn: String,
)
