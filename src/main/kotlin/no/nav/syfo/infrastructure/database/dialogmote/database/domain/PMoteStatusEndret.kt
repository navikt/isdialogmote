package no.nav.syfo.infrastructure.database.dialogmote.database.domain

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteStatusEndret
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class PMoteStatusEndret(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val motedeltakerBehandler: Boolean,
    val status: Dialogmote.Status,
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
        motedeltakerBehandler = this.motedeltakerBehandler,
        status = this.status,
        opprettetAv = this.opprettetAv,
        tilfelleStart = this.tilfelleStart,
        publishedAt = this.publishedAt,
    )
