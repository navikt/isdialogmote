package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: List<DialogmotedeltakerBehandlerVarselSvarDTO>,
) {
    companion object {
        fun from(varsel: DialogmotedeltakerBehandlerVarsel): DialogmotedeltakerBehandlerVarselDTO {
            return DialogmotedeltakerBehandlerVarselDTO(
                uuid = varsel.uuid.toString(),
                createdAt = varsel.createdAt,
                varselType = varsel.varselType.name,
                document = varsel.document,
                fritekst = varsel.fritekst,
                svar = varsel.svar.map { svar ->
                    DialogmotedeltakerBehandlerVarselSvarDTO.from(svar)
                },
            )
        }
    }
}
