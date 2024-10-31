package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import java.time.LocalDateTime

// TODO: Nå får man en liste med alle endringer på en person, som igjen er knyttet til et møte.
// Burde det vært en liste med møter, og så alle endringer knyttet til det møtet?

data class DialogmoteStatusEndringDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val dialogmote: MoteDTO,
    val status: DialogmoteStatus,
    val opprettetAv: String,
)

data class MoteDTO(
    val id: Int,
    val tid: LocalDateTime,
    val sted: String,
    val opprettetAv: String,
)
