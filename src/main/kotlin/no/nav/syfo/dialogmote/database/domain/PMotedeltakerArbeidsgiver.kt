package no.nav.syfo.dialogmote.database.domain

import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.UUID

data class PMotedeltakerArbeidsgiver(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val virksomhetsnummer: Virksomhetsnummer,
)

fun PMotedeltakerArbeidsgiver.toDialogmotedeltakerArbeidsgiver(
    dialogmotedeltakerArbeidsgiverVarsel: List<DialogmotedeltakerArbeidsgiverVarsel>,
    narmesteLederDTO: NarmesteLederDTO
) =
    DialogmotedeltakerArbeidsgiver(
        id = this.id,
        uuid = this.uuid,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        moteId = this.moteId,
        virksomhetsnummer = this.virksomhetsnummer,
        lederNavn = narmesteLederDTO.navn,
        lederEpost = narmesteLederDTO.narmesteLederEpost,
        type = DialogmotedeltakerType.ARBEIDSGIVER,
        varselList = dialogmotedeltakerArbeidsgiverVarsel,
    )
