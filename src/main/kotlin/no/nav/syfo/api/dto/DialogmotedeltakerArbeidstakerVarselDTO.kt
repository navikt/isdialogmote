package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerArbeidstakerVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val digitalt: Boolean,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val brevBestiltTidspunkt: LocalDateTime?,
    val svar: DialogmotedeltakerArbeidstakerVarselSvarDTO?,
) {
    companion object {
        fun from(varsel: DialogmotedeltakerArbeidstakerVarsel): DialogmotedeltakerArbeidstakerVarselDTO {
            return DialogmotedeltakerArbeidstakerVarselDTO(
                uuid = varsel.uuid.toString(),
                createdAt = varsel.createdAt,
                varselType = varsel.varselType.name,
                digitalt = varsel.digitalt,
                lestDato = varsel.lestDatoArbeidstaker,
                document = varsel.document,
                brevBestiltTidspunkt = varsel.brevBestiltTidspunkt,
                fritekst = varsel.fritekst,
                svar = varsel.svarType?.let {
                    DialogmotedeltakerArbeidstakerVarselSvarDTO(
                        svarTidspunkt = varsel.svarTidspunkt!!,
                        svarType = it.name,
                        svarTekst = varsel.svarTekst,
                    )
                },
            )
        }
    }
}
