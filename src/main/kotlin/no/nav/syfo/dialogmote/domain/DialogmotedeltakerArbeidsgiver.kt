package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidsgiverDTO
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.UUID

data class DialogmotedeltakerArbeidsgiver(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val virksomhetsnummer: Virksomhetsnummer,
    val lederNavn: String?,
    val lederEpost: String?,
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
