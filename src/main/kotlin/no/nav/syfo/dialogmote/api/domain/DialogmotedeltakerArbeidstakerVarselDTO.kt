package no.nav.syfo.dialogmote.api.domain

import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidstakerVarselDTO(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val varselType: String,
    val digitalt: Boolean,
    val pdf: ByteArray,
    val lestDato: LocalDateTime?,
)
