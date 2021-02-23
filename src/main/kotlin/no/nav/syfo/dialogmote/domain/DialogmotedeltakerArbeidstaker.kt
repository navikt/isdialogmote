package no.nav.syfo.dialogmote.domain

import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerArbeidstakerDTO
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val personIdent: PersonIdentNumber,
    val type: DialogmotedeltakerType,
    val varselList: List<DialogmotedeltakerArbeidstakerVarsel>,
)

fun DialogmotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstakerDTO() =
    DialogmotedeltakerArbeidstakerDTO(
        uuid = this.uuid.toString(),
        personIdent = this.personIdent.value,
        type = this.type.name,
        varselList = this.varselList.map {
            it.toDialogmotedeltakerArbeidstakerVarselDTO()
        }
    )
