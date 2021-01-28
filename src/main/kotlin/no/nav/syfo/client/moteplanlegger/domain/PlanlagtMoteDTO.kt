package no.nav.syfo.client.moteplanlegger.domain

import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

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
