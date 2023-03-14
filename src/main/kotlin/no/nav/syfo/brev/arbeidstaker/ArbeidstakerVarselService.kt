package no.nav.syfo.brev.arbeidstaker

import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.HendelseType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidstakerVarselService(
    private val brukernotifikasjonProducer: BrukernotifikasjonProducer,
    private val esyfovarselProducer: EsyfovarselProducer,
    private val namespace: String,
    private val appname: String,
) {
    private val log: Logger = LoggerFactory.getLogger(ArbeidstakerVarselService::class.java)

    fun sendVarsel(varseltype: MotedeltakerVarselType, personIdent: PersonIdent) {
        val hendelse = ArbeidstakerHendelse(
            type = getArbeidstakerVarselType(varseltype),
            arbeidstakerFnr = personIdent.value,
            data = null,
            orgnummer = null,
        )
        log.info("Skal sende ${getArbeidstakerVarselType(varseltype)} via til esyfovarselProducer")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    fun lesVarsel(
        personIdent: PersonIdent,
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
    personIdent: PersonIdent,
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
    .withPrefererteKanaler(PreferertKanal.SMS)
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
    .withPrefererteKanaler(PreferertKanal.SMS)
    .build()

fun createBrukernotifikasjonDone(): DoneInput = DoneInputBuilder()
    .withTidspunkt(LocalDateTime.now().toLocalDateTimeUTC())
    .build()

fun LocalDateTime.toLocalDateTimeUTC() =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

fun getArbeidstakerVarselType(motedeltakerVarselType: MotedeltakerVarselType): HendelseType {
    return when (motedeltakerVarselType) {
        MotedeltakerVarselType.INNKALT -> HendelseType.SM_DIALOGMOTE_INNKALT
        MotedeltakerVarselType.AVLYST -> HendelseType.SM_DIALOGMOTE_AVLYST
        MotedeltakerVarselType.NYTT_TID_STED -> HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
        MotedeltakerVarselType.REFERAT -> HendelseType.SM_DIALOGMOTE_REFERAT
    }
}
