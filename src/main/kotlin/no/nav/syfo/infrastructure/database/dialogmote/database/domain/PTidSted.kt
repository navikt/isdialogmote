package no.nav.syfo.infrastructure.database.dialogmote.database.domain

import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import java.time.LocalDateTime
import java.util.UUID

data class PTidSted(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
)

fun PTidSted.toDialogmoteTidSted() =
    DialogmoteTidSted(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        sted = this.sted,
        tid = this.tid,
        videoLink = this.videoLink
    )
