package no.nav.syfo.api.dto

import java.time.LocalDateTime

data class DialogmotedeltakerArbeidstakerVarselSvarDTO(
    val svarTidspunkt: LocalDateTime,
    val svarType: String,
    val svarTekst: String?,
)
