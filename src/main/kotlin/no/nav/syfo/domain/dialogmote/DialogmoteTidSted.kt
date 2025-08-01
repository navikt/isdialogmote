package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.DialogmoteTidStedDTO
import java.time.LocalDateTime
import java.util.UUID

data class DialogmoteTidSted(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?
)

fun DialogmoteTidSted.toDialogmoteTidStedDTO() =
    DialogmoteTidStedDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        sted = this.sted,
        tid = this.tid,
        videoLink = this.videoLink,
    )

fun List<DialogmoteTidSted>.latest() =
    this.maxByOrNull { it.createdAt }
