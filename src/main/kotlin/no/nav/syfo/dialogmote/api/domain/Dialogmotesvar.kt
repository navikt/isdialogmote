package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class Dialogmotesvar(
    val moteuuid: UUID,
    val dbRef: UUID,
    val ident: PersonIdent,
    val svarType: DialogmoteSvarType,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

data class Arbeidsgiversvar(
    val moteuuid: UUID,
    val varseluuid: UUID,
    val ident: PersonIdent,
    val svarType: DialogmoteSvarType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

data class Arbeidstakersvar(
    val moteuuid: UUID,
    val varseluuid: UUID,
    val ident: PersonIdent,
    val svarType: DialogmoteSvarType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

data class Behandlersvar(
    val moteuuid: UUID,
    val svaruuid: UUID,
    val ident: PersonIdent,
    val svarType: DialogmoteSvarType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

data class KDialogmotesvar(
    val ident: PersonIdent,
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

fun Arbeidstakersvar.toDialogmotesvar() = Dialogmotesvar(
    moteuuid = this.moteuuid,
    dbRef = this.varseluuid,
    ident = this.ident,
    svarType = this.svarType,
    senderType = SenderType.ARBEIDSTAKER,
    brevSentAt = this.brevSentAt,
    svarReceivedAt = this.svarReceivedAt,
)

fun Arbeidsgiversvar.toDialogmotesvar() = Dialogmotesvar(
    moteuuid = this.moteuuid,
    dbRef = this.varseluuid,
    ident = this.ident,
    svarType = this.svarType,
    senderType = SenderType.ARBEIDSGIVER,
    brevSentAt = this.brevSentAt,
    svarReceivedAt = this.svarReceivedAt,
)

fun Behandlersvar.toDialogmotesvar() = Dialogmotesvar(
    moteuuid = this.moteuuid,
    dbRef = this.svaruuid,
    ident = this.ident,
    svarType = this.svarType,
    senderType = SenderType.BEHANDLER,
    brevSentAt = this.brevSentAt,
    svarReceivedAt = this.svarReceivedAt,
)
