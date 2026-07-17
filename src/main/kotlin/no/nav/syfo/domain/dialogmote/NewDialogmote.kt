package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

data class NewDialogmote(
    val status: Dialogmote.Status,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstaker,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiver,
    val behandler: NewDialogmotedeltakerBehandler? = null,
    val tidSted: NewDialogmoteTidSted,
)

data class NewDialogmotedeltakerArbeidstaker(
    val personident: Personident,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerArbeidsgiver(
    val virksomhetsnummer: Virksomhetsnummer,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerBehandler(
    val personident: Personident?,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmoteTidSted(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String? = "",
) : TidStedDTO()
