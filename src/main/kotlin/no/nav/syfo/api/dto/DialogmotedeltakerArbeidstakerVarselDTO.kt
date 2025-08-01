package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerArbeidstakerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val digitalt: Boolean,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val brevBestiltTidspunkt: LocalDateTime?,
    val svar: DialogmotedeltakerArbeidstakerVarselSvarDTO?,
)
