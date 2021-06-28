package no.nav.syfo.brev.arbeidstaker

import no.nav.brukernotifikasjon.schemas.*
import no.nav.brukernotifikasjon.schemas.builders.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import java.net.URL
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
        motedeltakerArbeidstakerUuid: UUID,
        varselUuid: UUID,
    ) {
        val nokkel = createBrukernotifikasjonNokkel(
            serviceuser = serviceuserUsername,
            varselUuid = varselUuid,
        )
        val tekst = when (type) {
            MotedeltakerVarselType.INNKALT -> {
                "Du har mottatt et brev om innkalling til dialogmøte"
            }
            MotedeltakerVarselType.AVLYST -> {
                "Du har mottatt et brev om avlyst dialogmøte"
            }
            MotedeltakerVarselType.NYTT_TID_STED -> {
                "Du har mottatt et brev om endret dialogmøte"
            }
            MotedeltakerVarselType.REFERAT -> {
                "Du har mottatt et referat fra dialogmøte"
            }
        }
        val oppgave = createBrukernotifikasjonOppgave(
            createdAt = createdAt,
            personIdent = personIdent,
            tekst = tekst,
            link = URL(dialogmoteArbeidstakerUrl),
            grupperingsId = motedeltakerArbeidstakerUuid,
        )
        brukernotifikasjonProducer.sendOppgave(
            nokkel,
            oppgave,
        )
    }

    fun lesVarsel(
        personIdent: PersonIdentNumber,
        motedeltakerArbeidstakerUuid: UUID,
        varselUuid: UUID,
    ) {
        val nokkel = createBrukernotifikasjonNokkel(
            serviceuser = serviceuserUsername,
            varselUuid = varselUuid
        )
        val done = createBrukernotifikasjonDone(
            personIdent = personIdent,
            grupperingsId = motedeltakerArbeidstakerUuid,
        )
        brukernotifikasjonProducer.sendDone(
            nokkel,
            done,
        )
    }
}

fun createBrukernotifikasjonNokkel(
    serviceuser: String,
    varselUuid: UUID,
): Nokkel = NokkelBuilder()
    .withSystembruker(serviceuser)
    .withEventId(varselUuid.toString())
    .build()

fun createBrukernotifikasjonOppgave(
    createdAt: LocalDateTime,
    tekst: String,
    link: URL,
    personIdent: PersonIdentNumber,
    grupperingsId: UUID,
): Oppgave = OppgaveBuilder()
    .withTidspunkt(createdAt)
    .withGrupperingsId(grupperingsId.toString())
    .withFodselsnummer(personIdent.value)
    .withTekst(tekst)
    .withLink(link)
    .withSikkerhetsnivaa(4)
    .withEksternVarsling(true)
    .build()

fun createBrukernotifikasjonDone(
    personIdent: PersonIdentNumber,
    grupperingsId: UUID,
): Done = DoneBuilder()
    .withTidspunkt(LocalDateTime.now())
    .withFodselsnummer(personIdent.value)
    .withGrupperingsId(grupperingsId.toString())
    .build()
