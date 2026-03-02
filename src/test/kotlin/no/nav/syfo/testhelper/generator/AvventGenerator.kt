package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

fun generateAvvent(
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_FNR,
    isLukket: Boolean = false,
) = Avvent(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(),
    frist = LocalDate.now().plusWeeks(2),
    createdBy = UserConstants.VEILEDER_IDENT,
    personident = personident,
    beskrivelse = "Venter på noe",
    isLukket = isLukket,
)
