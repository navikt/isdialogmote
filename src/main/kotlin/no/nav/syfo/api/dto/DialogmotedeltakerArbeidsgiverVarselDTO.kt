package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerArbeidsgiverVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: DialogmotedeltakerArbeidsgiverVarselSvarDTO?,
)
