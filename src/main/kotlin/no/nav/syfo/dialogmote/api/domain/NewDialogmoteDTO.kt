package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

data class NewDialogmoteDTO(
    val arbeidstaker: NewDialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiverDTO,
    val behandler: NewDialogmotedeltakerBehandlerDTO? = null,
    val tidSted: NewDialogmoteTidStedDTO,
)

data class NewDialogmotedeltakerArbeidstakerDTO(
    val personIdent: String,
    val fritekstInnkalling: String?,
    val innkalling: List<DocumentComponentDTO>,
)

data class NewDialogmotedeltakerArbeidsgiverDTO(
    val virksomhetsnummer: String,
    val fritekstInnkalling: String?,
    val innkalling: List<DocumentComponentDTO>,
)

data class NewDialogmotedeltakerBehandlerDTO(
    val personIdent: String?,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val fritekstInnkalling: String?,
    val innkalling: List<DocumentComponentDTO>,
)

data class NewDialogmoteTidStedDTO(
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
)

fun NewDialogmoteDTO.toNewDialogmote(
    requestByNAVIdent: String,
    navEnhet: EnhetNr,
): NewDialogmote {
    return NewDialogmote(
        status = DialogmoteStatus.INNKALT,
        tildeltVeilederIdent = requestByNAVIdent,
        tildeltEnhet = navEnhet.value,
        opprettetAv = requestByNAVIdent,
        arbeidstaker = NewDialogmotedeltakerArbeidstaker(
            personIdent = PersonIdentNumber(this.arbeidstaker.personIdent),
            fritekstInnkalling = this.arbeidstaker.fritekstInnkalling,
        ),
        arbeidsgiver = NewDialogmotedeltakerArbeidsgiver(
            virksomhetsnummer = Virksomhetsnummer(this.arbeidsgiver.virksomhetsnummer),
            fritekstInnkalling = this.arbeidsgiver.fritekstInnkalling,
        ),
        behandler = this.behandler?.let {
            NewDialogmotedeltakerBehandler(
                personIdent = it.personIdent?.let { personident -> PersonIdentNumber(personident) },
                behandlerRef = it.behandlerRef,
                behandlerNavn = it.behandlerNavn,
                behandlerKontor = it.behandlerKontor,
                fritekstInnkalling = it.fritekstInnkalling,
            )
        },
        tidSted = NewDialogmoteTidSted(
            sted = tidSted.sted,
            tid = tidSted.tid,
            videoLink = tidSted.videoLink,
        ),
    )
}
