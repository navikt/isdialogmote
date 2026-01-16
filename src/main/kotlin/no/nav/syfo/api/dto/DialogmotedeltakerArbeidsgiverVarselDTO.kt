package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiverVarsel
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import java.time.LocalDateTime

data class DialogmotedeltakerArbeidsgiverVarselDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: DialogmotedeltakerArbeidsgiverVarselSvarDTO?,
) {
    companion object {
        fun from(varsel: DialogmotedeltakerArbeidsgiverVarsel): DialogmotedeltakerArbeidsgiverVarselDTO {
            return DialogmotedeltakerArbeidsgiverVarselDTO(
                uuid = varsel.uuid.toString(),
                createdAt = varsel.createdAt,
                varselType = varsel.varselType.name,
                lestDato = varsel.lestDatoArbeidsgiver,
                fritekst = varsel.fritekst,
                document = varsel.document,
                svar = varsel.svarType?.let {
                    DialogmotedeltakerArbeidsgiverVarselSvarDTO(
                        svarTidspunkt = varsel.svarTidspunkt!!,
                        svarType = it.name,
                        svarTekst = varsel.svarTekst,
                    )
                },
            )
        }
    }
}
