package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import java.time.LocalDateTime
import java.util.*

fun generatePDialogmote() = PDialogmote(
    id = 4,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    status = Dialogmote.Status.INNKALT.name,
    opprettetAv = "X000000",
    tildeltVeilederIdent = "X000000",
    tildeltEnhet = "0314",
)
