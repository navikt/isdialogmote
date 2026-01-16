package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandlerVarselSvar
import java.time.LocalDateTime

data class DialogmotedeltakerBehandlerVarselSvarDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val svarType: String,
    val tekst: String,
) {
    companion object {
        fun from(svar: DialogmotedeltakerBehandlerVarselSvar): DialogmotedeltakerBehandlerVarselSvarDTO {
            return DialogmotedeltakerBehandlerVarselSvarDTO(
                uuid = svar.uuid.toString(),
                createdAt = svar.createdAt,
                svarType = svar.type.name,
                tekst = svar.tekst,
            )
        }
    }
}
