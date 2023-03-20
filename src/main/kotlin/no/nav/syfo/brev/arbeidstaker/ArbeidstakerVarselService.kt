package no.nav.syfo.brev.arbeidstaker

import java.util.*
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.brev.esyfovarsel.ArbeidstakerHendelseUUID
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

    fun sendVarsel(varseltype: MotedeltakerVarselType, personIdent: PersonIdent, varselUuid: UUID) {
        val hendelse = ArbeidstakerHendelse(
            type = getArbeidstakerVarselType(varseltype),
            arbeidstakerFnr = personIdent.value,
            data = ArbeidstakerHendelseUUID(varselUuid.toString()),
            orgnummer = null,
        )
        log.info("Skal sende ${getArbeidstakerVarselType(varseltype)} via til esyfovarselProducer")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    fun lesVarsel(
        personIdent: PersonIdent,
        varselUuid: UUID,
    ) {
        val hendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_LEST,
            arbeidstakerFnr = personIdent.value,
            data = ArbeidstakerHendelseUUID(varselUuid.toString()),
            orgnummer = null,
        )
        log.info("Skal sende lesT oppgave ${HendelseType.SM_DIALOGMOTE_LEST} via til esyfovarselProducer")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }
}

fun getArbeidstakerVarselType(motedeltakerVarselType: MotedeltakerVarselType): HendelseType {
    return when (motedeltakerVarselType) {
        MotedeltakerVarselType.INNKALT -> HendelseType.SM_DIALOGMOTE_INNKALT
        MotedeltakerVarselType.AVLYST -> HendelseType.SM_DIALOGMOTE_AVLYST
        MotedeltakerVarselType.NYTT_TID_STED -> HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
        MotedeltakerVarselType.REFERAT -> HendelseType.SM_DIALOGMOTE_REFERAT
    }
}
