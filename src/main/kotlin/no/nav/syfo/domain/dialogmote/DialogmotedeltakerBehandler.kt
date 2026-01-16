package no.nav.syfo.domain.dialogmote

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

fun DialogmotedeltakerBehandler.findInnkallingVarselUuid(): UUID {
    return varselList.last().uuid
}

fun DialogmotedeltakerBehandler.findParentVarselId(): String {
    val latestVarsel = varselList.first()
    return latestVarsel.svar.firstOrNull()?.msgId ?: latestVarsel.uuid.toString()
}
