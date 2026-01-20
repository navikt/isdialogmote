package no.nav.syfo.infrastructure.database.model

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.BehandlerType
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerType
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
    val personIdent: PersonIdent?,
    val mottarReferat: Boolean,
    val deltatt: Boolean,
)

fun PMotedeltakerBehandler.toDialogmotedeltakerBehandler(
    dialogmotedeltakerBehandlerVarsel: List<DialogmotedeltakerBehandlerVarsel>,
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
    deltatt = this.deltatt,
    mottarReferat = this.mottarReferat,
)
