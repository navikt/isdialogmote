package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.pdfgen.model.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.varsel.arbeidstaker.domain.ArbeidstakerVarselDTO
import java.time.LocalDateTime
import java.util.*

data class Dialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val planlagtMoteUuid: UUID?,
    val planlagtMoteBekreftetTidspunkt: LocalDateTime?,
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstaker,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiver,
    val tidStedList: List<DialogmoteTidSted>,
)

fun Dialogmote.toDialogmoteDTO() =
    DialogmoteDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        planlagtMoteUuid = this.planlagtMoteUuid?.toString(),
        planlagtMoteBekreftetTidspunkt = this.planlagtMoteBekreftetTidspunkt,
        status = this.status.name,
        opprettetAv = this.opprettetAv,
        tildeltVeilederIdent = this.tildeltVeilederIdent,
        tildeltEnhet = this.tildeltEnhet,
        arbeidstaker = this.arbeidstaker.toDialogmotedeltakerArbeidstakerDTO(),
        arbeidsgiver = this.arbeidsgiver.toDialogmotedeltakerArbeidsgiverDTO(),
        tidStedList = this.tidStedList.map {
            it.toDialogmoteTidStedDTO()
        },
    )

fun List<Dialogmote>.toArbeidstakerVarselDTOList(): List<ArbeidstakerVarselDTO> {
    return this.map { dialogmote ->
        dialogmote.arbeidstaker.varselList.map {
            it.toArbeidstakerVarselDTO(
                dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                virksomhetsummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            )
        }
    }.flatten()
}

fun Dialogmote.toPdfModelAvlysningArbeidstaker() =
    PdfModelAvlysningArbeidstaker(
        avlysning = AvlysningArbeidstaker(
            tidOgSted = AvlysningArbeidstakerTidOgSted(
                sted = this.tidStedList.latest()?.sted ?: ""
            ),
        ),
    )

fun Dialogmote.toPdfModelAvlysningArbeidsgiver() =
    PdfModelAvlysningArbeidsgiver(
        avlysning = AvlysningArbeidsgiver(
            tidOgSted = AvlysningArbeidsgiverTidOgSted(
                sted = this.tidStedList.latest()?.sted ?: ""
            ),
        ),
    )
