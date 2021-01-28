package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.mock.planlagtMoteDTO
import java.time.LocalDateTime
import java.util.*

fun generateMotedeltakerArbeidstaker(
    moteId: Int,
    personIdentNumber: PersonIdentNumber,
) = DialogmotedeltakerArbeidstaker(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    moteId = moteId,
    personIdent = personIdentNumber,
)

fun generateMotedeltakerArbeidsgiver(
    moteId: Int,
) = DialogmotedeltakerArbeidsgiver(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    moteId = moteId,
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    lederNavn = null,
    lederEpost = null,
)

fun generateDialogmote(personIdentNumber: PersonIdentNumber): Dialogmote {
    val planlagtMoteDTO = planlagtMoteDTO(personIdentNumber)
    val moteId = 1
    return Dialogmote(
        id = moteId,
        uuid = UUID.randomUUID(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        planlagtMoteUuid = UUID.fromString(planlagtMoteDTO.moteUuid),
        status = DialogmoteStatus.INNKALT,
        opprettetAv = planlagtMoteDTO.opprettetAv,
        tildeltVeilederIdent = planlagtMoteDTO.eier,
        tildeltEnhet = planlagtMoteDTO.navEnhet,
        arbeidstaker = generateMotedeltakerArbeidstaker(moteId, personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiver(moteId),
    )
}
