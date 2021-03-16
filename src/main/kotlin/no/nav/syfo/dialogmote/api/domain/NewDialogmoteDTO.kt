package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import java.time.LocalDateTime

data class NewDialogmoteDTO(
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiverDTO,
    val tidSted: NewDialogmoteTidStedDTO,
)

data class NewDialogmotedeltakerArbeidstakerDTO(
    val personIdent: String,
)

data class NewDialogmotedeltakerArbeidsgiverDTO(
    val virksomhetsnummer: String,
)

data class NewDialogmoteTidStedDTO(
    val sted: String,
    val tid: LocalDateTime,
)

fun NewDialogmoteDTO.toNewDialogmote(
    requestByNAVIdent: String,
    narmesteLeder: NarmesteLederDTO,
    navEnhet: EnhetNr,
): NewDialogmote {
    return NewDialogmote(
        status = DialogmoteStatus.INNKALT,
        tildeltVeilederIdent = requestByNAVIdent,
        tildeltEnhet = navEnhet.value,
        opprettetAv = requestByNAVIdent,
        arbeidstaker = NewDialogmotedeltakerArbeidstaker(
            personIdent = PersonIdentNumber(this.arbeidstaker.personIdent),
        ),
        arbeidsgiver = NewDialogmotedeltakerArbeidsgiver(
            virksomhetsnummer = Virksomhetsnummer(this.arbeidsgiver.virksomhetsnummer),
            lederNavn = narmesteLeder.navn,
            lederEpost = narmesteLeder.epost,
        ),
        tidSted = NewDialogmoteTidSted(
            sted = tidSted.sted,
            tid = tidSted.tid,
        )
    )
}
