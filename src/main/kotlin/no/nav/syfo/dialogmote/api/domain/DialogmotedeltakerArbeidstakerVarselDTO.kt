package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime

data class DialogmotedeltakerArbeidstakerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val digitalt: Boolean,
    val pdf: ByteArray,
    val lestDato: LocalDateTime?,
)
