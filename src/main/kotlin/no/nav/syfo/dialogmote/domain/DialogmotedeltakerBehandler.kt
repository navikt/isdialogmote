package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerBehandlerDTO
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDateTime
import java.util.UUID

data class DialogmotedeltakerBehandler(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val behandlerType: BehandlerType,
    val type: DialogmotedeltakerType,
    val personIdent: PersonIdent?,
    val varselList: List<DialogmotedeltakerBehandlerVarsel>,
    val deltatt: Boolean,
    val mottarReferat: Boolean,
)

fun DialogmotedeltakerBehandler.toDialogmotedeltakerBehandlerDTO() =
    DialogmotedeltakerBehandlerDTO(
        uuid = this.uuid.toString(),
        behandlerRef = this.behandlerRef,
        behandlerNavn = this.behandlerNavn,
        behandlerKontor = this.behandlerKontor,
        behandlerType = this.behandlerType.name,
        type = this.type.name,
        personIdent = this.personIdent?.value,
        varselList = this.varselList.map {
            it.toDialogmotedeltakerBehandlerVarselDTO()
        },
        deltatt = this.deltatt,
        mottarReferat = this.mottarReferat,
    )

fun DialogmotedeltakerBehandler.findInnkallingVarselUuid(): UUID {
    return varselList.last().uuid
}

fun DialogmotedeltakerBehandler.findParentVarselId(): String {
    val latestVarsel = varselList.first()
    return latestVarsel.svar.firstOrNull()?.msgId ?: latestVarsel.uuid.toString()
}
