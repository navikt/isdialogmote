package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.mock.planlagtMoteDTO
import java.time.LocalDateTime
import java.util.UUID

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
    lederNavn = null,
    lederEpost = null,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver"
)

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
