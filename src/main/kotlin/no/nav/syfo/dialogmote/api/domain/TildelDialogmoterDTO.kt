package no.nav.syfo.dialogmote.api.domain

import java.util.UUID

data class TildelDialogmoterDTO(
    val veilederIdent: String,
    val dialogmoteUuids: List<UUID>,
)
