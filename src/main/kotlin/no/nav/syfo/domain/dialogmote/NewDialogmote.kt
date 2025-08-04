package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

data class NewDialogmote(
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstaker,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiver,
    val behandler: NewDialogmotedeltakerBehandler? = null,
    val tidSted: NewDialogmoteTidSted,
)

data class NewDialogmotedeltakerArbeidstaker(
    val personIdent: PersonIdent,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerArbeidsgiver(
    val virksomhetsnummer: Virksomhetsnummer,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerBehandler(
    val personIdent: PersonIdent?,
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
