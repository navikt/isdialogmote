package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: List<DialogmotedeltakerBehandlerVarselSvarDTO>,
)
