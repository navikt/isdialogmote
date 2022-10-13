package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import java.time.LocalDateTime

fun generateNewDialogmoteTidSted() = NewDialogmoteTidSted(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = "https://meet.google.com/xyz"
)

fun generateMotedeltakerArbeidstaker(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstaker(
    personIdent = personIdentNumber,
    fritekstInnkalling = "Ipsum lorum arbeidstaker"
)

fun generateMotedeltakerArbeidsgiver() = NewDialogmotedeltakerArbeidsgiver(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver"
)

fun generateMotedeltakerBehandler() = NewDialogmotedeltakerBehandler(
    personIdent = UserConstants.BEHANDLER_FNR,
    behandlerRef = "1234",
    behandlerNavn = UserConstants.BEHANDLER_NAVN,
    behandlerKontor = UserConstants.BEHANDLER_KONTOR,
)

fun generateNewDialogmote(
    personIdentNumber: PersonIdentNumber,
    status: DialogmoteStatus = DialogmoteStatus.INNKALT,
): NewDialogmote = NewDialogmote(
    status = status,
    opprettetAv = UserConstants.VEILEDER_IDENT,
    tildeltVeilederIdent = UserConstants.VEILEDER_IDENT,
    tildeltEnhet = UserConstants.ENHET_NR.value,
    arbeidstaker = generateMotedeltakerArbeidstaker(personIdentNumber),
    arbeidsgiver = generateMotedeltakerArbeidsgiver(),
    tidSted = generateNewDialogmoteTidSted()
)

fun generateNewDialogmoteWithBehandler(
    personIdentNumber: PersonIdentNumber,
    status: DialogmoteStatus = DialogmoteStatus.INNKALT,
): NewDialogmote = NewDialogmote(
    status = status,
    opprettetAv = UserConstants.VEILEDER_IDENT,
    tildeltVeilederIdent = UserConstants.VEILEDER_IDENT,
    tildeltEnhet = UserConstants.ENHET_NR.value,
    arbeidstaker = generateMotedeltakerArbeidstaker(personIdentNumber),
    arbeidsgiver = generateMotedeltakerArbeidsgiver(),
    tidSted = generateNewDialogmoteTidSted(),
    behandler = generateMotedeltakerBehandler(),
)
