package no.nav.syfo.dialogmote.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

data class NewDialogmote(
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstaker,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiver,
    val tidSted: NewDialogmoteTidSted,
)

data class NewDialogmotedeltakerArbeidstaker(
    val personIdent: PersonIdentNumber,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerArbeidsgiver(
    val virksomhetsnummer: Virksomhetsnummer,
    val fritekstInnkalling: String? = "",
)

data class NewDialogmoteTidSted(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String? = "",
) : TidStedDTO()
