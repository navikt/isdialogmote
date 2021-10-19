package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val document: List<DocumentComponentDTO>,
)
