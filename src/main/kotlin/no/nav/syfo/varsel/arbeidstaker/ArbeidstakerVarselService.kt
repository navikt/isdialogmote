package no.nav.syfo.varsel.arbeidstaker

import no.nav.brukernotifikasjon.schemas.*
import no.nav.brukernotifikasjon.schemas.builders.OppgaveBuilder
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class ArbeidstakerVarselService(
    private val brukernotifikasjonProducer: BrukernotifikasjonProducer,
    private val dialogmoteArbeidstakerUrl: String,
    private val serviceuserUsername: String,
) {
    fun sendVarsel(
        createdAt: LocalDateTime,
        personIdent: PersonIdentNumber,
        type: MotedeltakerVarselType,
        varselUuid: UUID,
    ) {
        val nokkel = Nokkel(
            serviceuserUsername,
            varselUuid.toString()
        )
        when (type) {
            MotedeltakerVarselType.INNKALT -> {
                val oppgave = createBrukernotifikasjonOppgave(
                    createdAt = createdAt,
                    personIdent = personIdent,
                    tekst = "Du har mottatt et brev om innkalling til dialogmøte",
                    link = URL(dialogmoteArbeidstakerUrl),
                    uuid = varselUuid,
                )
                brukernotifikasjonProducer.sendOppgave(
                    nokkel,
                    oppgave,
                )
            }
            MotedeltakerVarselType.AVLYST -> {
                val oppgave = createBrukernotifikasjonOppgave(
                    createdAt = createdAt,
                    personIdent = personIdent,
                    tekst = "Du har mottatt et brev om avlyst dialogmøte",
                    link = URL(dialogmoteArbeidstakerUrl),
                    uuid = varselUuid,
                )
                brukernotifikasjonProducer.sendOppgave(
                    nokkel,
                    oppgave,
                )
            }
            MotedeltakerVarselType.NYTT_TID_STED -> {
                val oppgave = createBrukernotifikasjonOppgave(
                    createdAt = createdAt,
                    personIdent = personIdent,
                    tekst = "Du har mottatt et brev om endret dialogmøte",
                    link = URL(dialogmoteArbeidstakerUrl),
                    uuid = varselUuid,
                )
                brukernotifikasjonProducer.sendOppgave(
                    nokkel,
                    oppgave,
                )
            }
        }
    }

    fun lesVarsel(
        personIdent: PersonIdentNumber,
        varselUuid: UUID,
    ) {
        val nokkel = Nokkel(
            serviceuserUsername,
            varselUuid.toString()
        )
        val done = createBrukernotifikasjonDone(
            personIdent = personIdent,
            uuid = varselUuid,
        )
        brukernotifikasjonProducer.sendDone(
            nokkel,
            done,
        )
    }
}

fun createBrukernotifikasjonOppgave(
    createdAt: LocalDateTime,
    tekst: String,
    link: URL,
    personIdent: PersonIdentNumber,
    uuid: UUID,
): Oppgave = OppgaveBuilder()
    .withTidspunkt(createdAt)
    .withGrupperingsId(uuid.toString())
    .withFodselsnummer(personIdent.value)
    .withTekst(tekst)
    .withLink(link)
    .withSikkerhetsnivaa(4)
    .withEksternVarsling(true)
    .build()

fun createBrukernotifikasjonDone(
    personIdent: PersonIdentNumber,
    uuid: UUID,
) = Done(
    Instant.now().toEpochMilli(),
    personIdent.value,
    uuid.toString(),
)
