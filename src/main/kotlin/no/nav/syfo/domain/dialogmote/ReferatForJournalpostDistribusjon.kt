package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDateTime

data class ReferatForJournalpostDistribusjon(
    val referatId: Int,
    val personIdent: PersonIdent,
    val referatJournalpostId: String?,
    val motetidspunkt: LocalDateTime?
)
