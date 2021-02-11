package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.DialogmoteTidSted
import java.time.LocalDateTime
import java.util.*

data class PTidSted(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val sted: String,
    val tid: LocalDateTime,
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
    )
