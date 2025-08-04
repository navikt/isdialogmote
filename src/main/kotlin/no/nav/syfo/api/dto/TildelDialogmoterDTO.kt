package no.nav.syfo.api.dto

import java.util.UUID

data class TildelDialogmoterDTO(
    val veilederIdent: String,
    val dialogmoteUuids: List<UUID>,
)
