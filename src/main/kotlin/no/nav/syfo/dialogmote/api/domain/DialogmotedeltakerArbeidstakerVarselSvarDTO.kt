package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime

data class DialogmotedeltakerArbeidstakerVarselSvarDTO(
    val svarTidspunkt: LocalDateTime,
    val svarType: String,
    val svarTekst: String?,
)
