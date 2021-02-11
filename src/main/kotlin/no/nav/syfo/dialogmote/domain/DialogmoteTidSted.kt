package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmoteTidStedDTO
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
)

fun DialogmoteTidSted.toDialogmoteTidStedDTO() =
    DialogmoteTidStedDTO(
        uuid = this.uuid.toString(),
        sted = this.sted,
        tid = this.tid,
    )

fun DialogmoteTidSted.passed(): Boolean = this.tid.isAfter(LocalDateTime.now())

fun List<DialogmoteTidSted>.latest() =
    this.maxByOrNull { it.tid }
