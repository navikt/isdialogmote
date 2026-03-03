package no.nav.syfo.infrastructure.database.model

import no.nav.syfo.domain.dialogmoteflyt.DialogmoteFlyt
import no.nav.syfo.domain.dialogmoteflyt.DialogmoteFlytEndring
import java.time.LocalDateTime
import java.util.*

data class PDialogmoteFlyt(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun PDialogmoteFlyt.toDialogmoteFlyt(
    endringer: List<DialogmoteFlytEndring>,
): DialogmoteFlyt =
    DialogmoteFlyt(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        endringer = endringer.sortedBy { it.createdAt },
    )
