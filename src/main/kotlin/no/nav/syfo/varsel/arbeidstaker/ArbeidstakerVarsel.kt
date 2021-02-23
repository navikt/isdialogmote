package no.nav.syfo.varsel.arbeidstaker

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.builders.OppgaveBuilder
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.varsel.VarselType
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

data class ArbeidstakerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val type: VarselType,
    val opprettetAv: String,
    val personIdent: PersonIdentNumber,
)

fun ArbeidstakerVarsel.toBrukernotifikasjonOppgave(
    tekst: String,
    link: URL,
) = OppgaveBuilder()
    .withTidspunkt(this.createdAt)
    .withGrupperingsId(this.uuid.toString())
    .withFodselsnummer(this.personIdent.value)
    .withTekst(tekst)
    .withLink(link)
    .withSikkerhetsnivaa(4)
    .build()

fun ArbeidstakerVarsel.toBrukernotifikasjonDone() = Done(
    Instant.now().toEpochMilli(),
    this.personIdent.value,
    this.uuid.toString(),
)
