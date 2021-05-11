package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerArbeidsgiverVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
)
