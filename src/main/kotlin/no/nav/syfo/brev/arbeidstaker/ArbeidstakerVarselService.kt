package no.nav.syfo.brev.arbeidstaker

import no.nav.brukernotifikasjon.schemas.builders.*
import no.nav.brukernotifikasjon.schemas.input.*
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdentNumber
import java.net.URL
import java.time.*
import java.util.*

class ArbeidstakerVarselService(
    private val brukernotifikasjonProducer: BrukernotifikasjonProducer,
    private val dialogmoteArbeidstakerUrl: String,
    private val namespace: String,
    private val appname: String,
) {
    fun sendVarsel(
        createdAt: LocalDateTime,
        personIdent: PersonIdentNumber,
        type: MotedeltakerVarselType,
        motedeltakerArbeidstakerUuid: UUID,
        varselUuid: UUID,
    ) {
        val nokkel = createBrukernotifikasjonNokkel(
            varselUuid = varselUuid,
            grupperingsId = motedeltakerArbeidstakerUuid,
            personIdent = personIdent,
            namespace = namespace,
            appname = appname,
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
        if (type == MotedeltakerVarselType.INNKALT || type == MotedeltakerVarselType.NYTT_TID_STED) {
            val oppgave = createBrukernotifikasjonOppgave(
                createdAt = createdAt,
                tekst = tekst,
                link = URL(dialogmoteArbeidstakerUrl),
            )
            brukernotifikasjonProducer.sendOppgave(
                nokkel,
                oppgave,
            )
        } else {
            val beskjed = createBrukernotifikasjonBeskjed(
                createdAt = createdAt,
                tekst = tekst,
                link = URL(dialogmoteArbeidstakerUrl),
            )
            brukernotifikasjonProducer.sendBeskjed(
                nokkel,
                beskjed,
            )
        }
    }

    fun lesVarsel(
        personIdent: PersonIdentNumber,
        motedeltakerArbeidstakerUuid: UUID,
        varselUuid: UUID,
    ) {
        val nokkel = createBrukernotifikasjonNokkel(
            varselUuid = varselUuid,
            grupperingsId = motedeltakerArbeidstakerUuid,
            personIdent = personIdent,
            namespace = namespace,
            appname = appname,
        )
        val done = createBrukernotifikasjonDone()

        brukernotifikasjonProducer.sendDone(
            nokkel,
            done,
        )
    }
}

fun createBrukernotifikasjonNokkel(
    varselUuid: UUID,
    grupperingsId: UUID,
    personIdent: PersonIdentNumber,
    namespace: String,
    appname: String,
): NokkelInput = NokkelInputBuilder()
    .withEventId(varselUuid.toString())
    .withNamespace(namespace)
    .withAppnavn(appname)
    .withGrupperingsId(grupperingsId.toString())
    .withFodselsnummer(personIdent.value)
    .build()

fun createBrukernotifikasjonBeskjed(
    createdAt: LocalDateTime,
    tekst: String,
    link: URL,
): BeskjedInput = BeskjedInputBuilder()
    .withTidspunkt(createdAt.toLocalDateTimeUTC())
    .withTekst(tekst)
    .withLink(link)
    .withSikkerhetsnivaa(4)
    .withEksternVarsling(true)
    .withSynligFremTil(LocalDateTime.now().plusYears(1))
    .build()

fun createBrukernotifikasjonOppgave(
    createdAt: LocalDateTime,
    tekst: String,
    link: URL,
): OppgaveInput = OppgaveInputBuilder()
    .withTidspunkt(createdAt.toLocalDateTimeUTC())
    .withTekst(tekst)
    .withLink(link)
    .withSikkerhetsnivaa(4)
    .withEksternVarsling(true)
    .build()

fun createBrukernotifikasjonDone(): DoneInput = DoneInputBuilder()
    .withTidspunkt(LocalDateTime.now().toLocalDateTimeUTC())
    .build()

fun LocalDateTime.toLocalDateTimeUTC() =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
