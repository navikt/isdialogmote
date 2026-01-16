package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler

data class DialogmotedeltakerBehandlerDTO(
    val uuid: String,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
    val behandlerType: String,
    val type: String,
    val personIdent: String?,
    val varselList: List<DialogmotedeltakerBehandlerVarselDTO>,
    val deltatt: Boolean,
    val mottarReferat: Boolean,
) {
    companion object {
        fun from(behandler: DialogmotedeltakerBehandler): DialogmotedeltakerBehandlerDTO {
            return DialogmotedeltakerBehandlerDTO(
                uuid = behandler.uuid.toString(),
                behandlerRef = behandler.behandlerRef,
                behandlerNavn = behandler.behandlerNavn,
                behandlerKontor = behandler.behandlerKontor,
                behandlerType = behandler.behandlerType.name,
                type = behandler.type.name,
                personIdent = behandler.personIdent?.value,
                varselList = behandler.varselList.map { DialogmotedeltakerBehandlerVarselDTO.from(it) },
                deltatt = behandler.deltatt,
                mottarReferat = behandler.mottarReferat,
            )
        }
    }
}
