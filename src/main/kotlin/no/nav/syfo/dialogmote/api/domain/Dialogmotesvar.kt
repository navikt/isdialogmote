package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdentNumber
import java.time.OffsetDateTime
import java.util.*

data class Dialogmotesvar(
    val moteuuid: UUID,
    val varseluuid: UUID,
    val ident: PersonIdentNumber,
    val svarType: DialogmoteSvarType,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

data class KDialogmotesvar(
    val ident: PersonIdentNumber,
    val svarType: DialogmoteSvarType,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

enum class SenderType {
    ARBEIDSTAKER,
    ARBEIDSGIVER,
    BEHANDLER,
}

fun Dialogmotesvar.toKDialogmotesvar() = KDialogmotesvar(
    ident = this.ident,
    svarType = this.svarType,
    senderType = this.senderType,
    brevSentAt = this.brevSentAt,
    svarReceivedAt = this.svarReceivedAt,
)
