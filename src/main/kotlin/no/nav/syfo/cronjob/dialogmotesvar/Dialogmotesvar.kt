package no.nav.syfo.cronjob.dialogmotesvar

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
    val svarTekst: String?,
)

data class KDialogmotesvar(
    val ident: PersonIdent,
    val svarType: DialogmoteSvarType,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
    val svarTekst: String?,
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
    svarTekst = this.svarTekst,
)
