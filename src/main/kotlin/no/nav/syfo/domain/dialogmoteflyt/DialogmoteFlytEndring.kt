package no.nav.syfo.domain.dialogmoteflyt

import no.nav.syfo.domain.dialogmote.Dialogmote
import java.time.LocalDateTime
import java.util.*

sealed interface DialogmoteFlytEndring {
    val createdAt: LocalDateTime

    data class AvventEndring(
        val avvent: Avvent,
    ) : DialogmoteFlytEndring {
        override val createdAt: LocalDateTime get() = avvent.createdAt
    }

    data class DialogmoteEndring(
        val moteUuid: UUID,
        val moteStatus: Dialogmote.Status,
        override val createdAt: LocalDateTime,
    ) : DialogmoteFlytEndring
}
