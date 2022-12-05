package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerBehandlerVarselSvar
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

data class PMotedeltakerBehandlerVarselSvar(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val motedeltakerBehandlerVarselId: Int,
    val svarType: String,
    val svarTekst: String,
    val msgId: String,
    val svarPublishedToKafkaAt: OffsetDateTime?,
    val valid: Boolean,
)

fun PMotedeltakerBehandlerVarselSvar.toDialogmoteDeltakerVarselSvar() = DialogmotedeltakerBehandlerVarselSvar(
    id = this.id,
    uuid = this.uuid,
    createdAt = this.createdAt,
    type = DialogmoteSvarType.valueOf(this.svarType),
    tekst = this.svarTekst,
    msgId = this.msgId,
)
