package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.mock.planlagtMoteDTO
import java.time.LocalDateTime
import java.util.*

fun generateNewDialogmoteTidSted() = NewDialogmoteTidSted(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = "https://meet.google.com/xyz"
)

fun generateNewDialogmoteTidStedNoVideoLink() = NewDialogmoteTidSted(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30)
)

fun generateMotedeltakerArbeidstaker(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstaker(
    personIdent = personIdentNumber,
    fritekstInnkalling = "Ipsum lorum arbeidstaker"
)

fun generateMotedeltakerArbeidstakerNoFritekst(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstaker(
    personIdent = personIdentNumber
)

fun generateMotedeltakerArbeidsgiver() = NewDialogmotedeltakerArbeidsgiver(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    lederNavn = null,
    lederEpost = null,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver"
)

fun generateMotedeltakerArbeidsgiverNoFritekst() = NewDialogmotedeltakerArbeidsgiver(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    lederNavn = null,
    lederEpost = null
)

fun generateNewDialogmote(
    personIdentNumber: PersonIdentNumber
): NewDialogmote {
    return NewDialogmote(
        status = DialogmoteStatus.INNKALT,
        opprettetAv = UserConstants.VEILEDER_IDENT,
        tildeltVeilederIdent = UserConstants.VEILEDER_IDENT,
        tildeltEnhet = UserConstants.ENHET_NR.value,
        arbeidstaker = generateMotedeltakerArbeidstaker(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiver(),
        tidSted = generateNewDialogmoteTidSted()
    )
}

fun generateNewDialogmotePlanlagt(
    personIdentNumber: PersonIdentNumber
): NewDialogmotePlanlagt {
    val planlagtMoteDTO = planlagtMoteDTO(personIdentNumber)
    return NewDialogmotePlanlagt(
        planlagtMoteUuid = UUID.fromString(planlagtMoteDTO.moteUuid),
        status = DialogmoteStatus.INNKALT,
        opprettetAv = planlagtMoteDTO.opprettetAv,
        tildeltVeilederIdent = planlagtMoteDTO.eier,
        tildeltEnhet = planlagtMoteDTO.navEnhet,
        arbeidstaker = generateMotedeltakerArbeidstaker(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiver(),
        tidSted = generateNewDialogmoteTidSted()
    )
}

fun generateNewDialogmotePlanlagtWithoutInnkallingTexts(
    personIdentNumber: PersonIdentNumber
): NewDialogmotePlanlagt {
    val planlagtMoteDTO = planlagtMoteDTO(personIdentNumber)
    return NewDialogmotePlanlagt(
        planlagtMoteUuid = UUID.fromString(planlagtMoteDTO.moteUuid),
        status = DialogmoteStatus.INNKALT,
        opprettetAv = planlagtMoteDTO.opprettetAv,
        tildeltVeilederIdent = planlagtMoteDTO.eier,
        tildeltEnhet = planlagtMoteDTO.navEnhet,
        arbeidstaker = generateMotedeltakerArbeidstakerNoFritekst(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverNoFritekst(),
        tidSted = generateNewDialogmoteTidStedNoVideoLink()
    )
}
