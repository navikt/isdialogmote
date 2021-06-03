package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DialogmoteStatusEndret
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class PMoteStatusEndret(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tilfelleStart: LocalDate,
    val publishedAt: LocalDateTime?,
)

fun PMoteStatusEndret.toDialogmoteStatusEndret() =
    DialogmoteStatusEndret(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        status = this.status,
        opprettetAv = this.opprettetAv,
        tilfelleStart = this.tilfelleStart,
        publishedAt = this.publishedAt,
    )
