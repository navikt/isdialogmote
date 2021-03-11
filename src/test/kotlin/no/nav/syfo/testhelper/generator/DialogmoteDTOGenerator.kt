package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.mock.planlagtMoteDTO
import java.time.LocalDateTime

fun generateNewDialogmoteTidStedDTO() = NewDialogmoteTidStedDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
)

fun generateMotedeltakerArbeidstakerDTO(
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdentNumber.value,
)

fun generateMotedeltakerArbeidsgiverDTO() = NewDialogmotedeltakerArbeidsgiverDTO(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
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
