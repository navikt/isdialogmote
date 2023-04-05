package no.nav.syfo.brev.arbeidstaker

import java.util.*
import no.nav.syfo.brev.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.brev.esyfovarsel.DialogmoteInnkallingArbeidstakerData
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.HendelseType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidstakerVarselService(
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    private val log: Logger = LoggerFactory.getLogger(ArbeidstakerVarselService::class.java)

    fun sendVarsel(varseltype: MotedeltakerVarselType, personIdent: PersonIdent, varselUuid: UUID, journalpostId: String?) {
        val hendelse = ArbeidstakerHendelse(
            type = getArbeidstakerHendelseType(varseltype),
            arbeidstakerFnr = personIdent.value,
            data = DialogmoteInnkallingArbeidstakerData(varselUuid.toString(), journalpostId),
            orgnummer = null,
        )
        log.info("Skal sende ${getArbeidstakerHendelseType(varseltype)} til esyfovarselProducer. Journalpostid: $journalpostId")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    fun lesVarsel(
        personIdent: PersonIdent,
        varselUuid: UUID,
    ) {
        val hendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_LEST,
            arbeidstakerFnr = personIdent.value,
            data = DialogmoteInnkallingArbeidstakerData(varselUuid.toString(), journalpostId = null),
            orgnummer = null,
        )
        log.info("Skal sende lest hendelse ${HendelseType.SM_DIALOGMOTE_LEST} til esyfovarselProducer")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }
}

fun getArbeidstakerHendelseType(motedeltakerVarselType: MotedeltakerVarselType): HendelseType {
    return when (motedeltakerVarselType) {
        MotedeltakerVarselType.INNKALT -> HendelseType.SM_DIALOGMOTE_INNKALT
        MotedeltakerVarselType.AVLYST -> HendelseType.SM_DIALOGMOTE_AVLYST
        MotedeltakerVarselType.NYTT_TID_STED -> HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
        MotedeltakerVarselType.REFERAT -> HendelseType.SM_DIALOGMOTE_REFERAT
    }
}
