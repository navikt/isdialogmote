package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerBehandler(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val behandlerType: String,
    val personIdent: PersonIdentNumber?,
)

fun PMotedeltakerBehandler.toDialogmotedeltakerBehandler(
    dialogmotedeltakerBehandlerVarsel: List<DialogmotedeltakerBehandlerVarsel>
) = DialogmotedeltakerBehandler(
    id = this.id,
    uuid = this.uuid,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    moteId = this.moteId,
    behandlerRef = this.behandlerRef,
    behandlerNavn = this.behandlerNavn,
    behandlerKontor = this.behandlerKontor,
    behandlerType = BehandlerType.valueOf(this.behandlerType),
    type = DialogmotedeltakerType.BEHANDLER,
    personIdent = this.personIdent,
    varselList = dialogmotedeltakerBehandlerVarsel,
)
