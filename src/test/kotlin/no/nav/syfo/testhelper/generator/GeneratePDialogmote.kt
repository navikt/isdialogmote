package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.database.domain.PDialogmote
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import java.time.LocalDateTime
import java.util.*

fun generatePDialogmote() = PDialogmote(
    id = 4,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    status = DialogmoteStatus.INNKALT.name,
    opprettetAv = "X000000",
    tildeltVeilederIdent = "X000000",
    tildeltEnhet = "0314",
)
