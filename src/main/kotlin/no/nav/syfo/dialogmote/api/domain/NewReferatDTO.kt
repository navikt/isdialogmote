package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.*

data class NewReferatDTO(
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val behandlerOppgave: String?,
    val narmesteLederNavn: String,
    val document: List<DocumentComponentDTO>,
    val andreDeltakere: List<NewDialogmotedeltakerAnnenDTO>,
    val behandlerDeltatt: Boolean?,
    val behandlerMottarReferat: Boolean?,
)

data class NewDialogmotedeltakerAnnenDTO(
    val funksjon: String,
    val navn: String,
)

fun NewReferatDTO.toNewReferat(
    moteId: Int,
    ferdigstilt: Boolean,
): NewReferat {
    return NewReferat(
        moteId = moteId,
        situasjon = situasjon,
        konklusjon = konklusjon,
        arbeidstakerOppgave = arbeidstakerOppgave,
        arbeidsgiverOppgave = arbeidsgiverOppgave,
        veilederOppgave = veilederOppgave,
        behandlerOppgave = behandlerOppgave,
        narmesteLederNavn = narmesteLederNavn,
        document = document,
        andreDeltakere = andreDeltakere.map {
            it.toNewDialogmotedeltakerAnnen()
        },
        ferdigstilt = ferdigstilt,
    )
}

fun NewDialogmotedeltakerAnnenDTO.toNewDialogmotedeltakerAnnen(): NewDialogmotedeltakerAnnen {
    return NewDialogmotedeltakerAnnen(
        funksjon = funksjon,
        navn = navn,
    )
}
