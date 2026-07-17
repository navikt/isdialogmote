package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.Personident
import java.time.LocalDateTime

data class ReferatForJournalpostDistribusjon(
    val referatId: Int,
    val personident: Personident,
    val referatJournalpostId: String?,
    val motetidspunkt: LocalDateTime?
)
