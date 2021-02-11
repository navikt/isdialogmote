package no.nav.syfo.client.moteplanlegger.domain

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class PlanlagtMoteDTO(
    val id: Long = 0,
    val moteUuid: String,
    val opprettetAv: String,
    val aktorId: String,
    val status: String,
    val fnr: String,
    val opprettetTidspunkt: LocalDateTime,
    val bekreftetTidspunkt: LocalDateTime? = null,
    val navEnhet: String,
    val eier: String,
    val deltakere: List<PlanlagtMoteDeltakerDTO>,
    val bekreftetAlternativ: PlanlagtTidOgStedDTO? = null,
    val alternativer: List<PlanlagtTidOgStedDTO>,
    val sistEndret: LocalDateTime? = null,
    val trengerBehandling: Boolean = false,
)

fun PlanlagtMoteDTO.virksomhetsnummer(): Virksomhetsnummer? {
    return this.deltakere.first {
        it.type == PlanlagtMoteDeltakerType.ARBEIDSGIVER.value
    }.orgnummer?.let {
        Virksomhetsnummer(it)
    }
}

fun PlanlagtMoteDTO.arbeidsgiver() = this.deltakere.first { it.type == PlanlagtMoteDeltakerType.ARBEIDSGIVER.value }
fun PlanlagtMoteDTO.tidStedValgt() = this.alternativer.first { it.valgt }

fun PlanlagtMoteDTO.toNewDialogmote(): NewDialogmote {
    val arbeidsgiver = this.arbeidsgiver()
    val tidSted = this.tidStedValgt()
    return NewDialogmote(
        planlagtMoteUuid = UUID.fromString(this.moteUuid),
        status = DialogmoteStatus.INNKALT,
        tildeltVeilederIdent = this.eier,
        tildeltEnhet = this.navEnhet,
        opprettetAv = this.opprettetAv,
        arbeidstaker = NewDialogmotedeltakerArbeidstaker(
            personIdent = PersonIdentNumber(this.fnr),
        ),
        arbeidsgiver = NewDialogmotedeltakerArbeidsgiver(
            virksomhetsnummer = Virksomhetsnummer(arbeidsgiver.orgnummer!!),
            lederNavn = arbeidsgiver.navn,
            lederEpost = arbeidsgiver.epost,
        ),
        tidSted = NewDialogmoteTidSted(
            sted = tidSted.sted,
            tid = tidSted.tid,
        )
    )
}
