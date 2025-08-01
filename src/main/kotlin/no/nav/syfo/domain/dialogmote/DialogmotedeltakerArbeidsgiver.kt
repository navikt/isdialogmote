package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.DialogmotedeltakerArbeidsgiverDTO
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidsgiver(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val virksomhetsnummer: Virksomhetsnummer,
    val type: DialogmotedeltakerType,
    val varselList: List<DialogmotedeltakerArbeidsgiverVarsel>,
)

fun DialogmotedeltakerArbeidsgiver.toDialogmotedeltakerArbeidsgiverDTO() =
    DialogmotedeltakerArbeidsgiverDTO(
        uuid = this.uuid.toString(),
        virksomhetsnummer = this.virksomhetsnummer.value,
        type = this.type.name,
        varselList = this.varselList.map {
            it.toDialogmotedeltakerArbeidsgiverVarselDTO()
        }
    )
