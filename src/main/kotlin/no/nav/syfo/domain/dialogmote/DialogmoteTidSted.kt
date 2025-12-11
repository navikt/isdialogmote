package no.nav.syfo.domain.dialogmote

import java.time.LocalDateTime
import java.util.*

data class DialogmoteTidSted(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
)

fun List<DialogmoteTidSted>.latest() =
    this.maxByOrNull { it.createdAt }
