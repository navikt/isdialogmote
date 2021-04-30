package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.mock.planlagtMoteDTO
import java.time.LocalDateTime

fun generateNewDialogmoteTidStedDTO() = NewDialogmoteTidStedDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = "https://meet.google.com/xyz"
)

fun generateNewDialogmoteTidStedDTONoVideoLink() = NewDialogmoteTidStedDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = null
)

fun generateMotedeltakerArbeidstakerDTO(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdentNumber.value,
    fritekstInnkalling = "Ipsum lorum arbeidstaker"
)

fun generateMotedeltakerArbeidstakerDTOMissingValues(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdentNumber.value,
    fritekstInnkalling = null
)

fun generateMotedeltakerArbeidsgiverDTO() = NewDialogmotedeltakerArbeidsgiverDTO(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver"
)

fun generateMotedeltakerArbeidsgiverDTOMissingValues() = NewDialogmotedeltakerArbeidsgiverDTO(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    fritekstInnkalling = null
)

fun generateNewDialogmoteDTO(
    personIdentNumber: PersonIdentNumber
): NewDialogmoteDTO {
    val planlagtMoteDTO = planlagtMoteDTO(personIdentNumber)
    return NewDialogmoteDTO(
        tildeltEnhet = planlagtMoteDTO.navEnhet,
        arbeidstaker = generateMotedeltakerArbeidstakerDTO(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTO(),
        tidSted = generateNewDialogmoteTidStedDTO()
    )
}

fun generateNewDialogmoteDTOWithMissingValues(
    personIdentNumber: PersonIdentNumber
): NewDialogmoteDTO {
    val planlagtMoteDTO = planlagtMoteDTO(personIdentNumber)
    return NewDialogmoteDTO(
        tildeltEnhet = planlagtMoteDTO.navEnhet,
        arbeidstaker = generateMotedeltakerArbeidstakerDTOMissingValues(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTOMissingValues(),
        tidSted = generateNewDialogmoteTidStedDTONoVideoLink()
    )
}