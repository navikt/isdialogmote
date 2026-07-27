package no.nav.syfo.application

import java.util.*
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselData
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselDataJournalpost
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselDataMotetidspunkt
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ArbeidstakerVarselService(
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    private val log: Logger = LoggerFactory.getLogger(ArbeidstakerVarselService::class.java)

    fun sendVarsel(
        varseltype: MotedeltakerVarselType,
        personident: Personident,
        varselUuid: UUID,
        journalpostId: String?,
        motetidspunkt: LocalDateTime?,
    ) {
        val hendelse = ArbeidstakerHendelse(
            type = getArbeidstakerHendelseType(varseltype),
            arbeidstakerFnr = personident.value,
            data = VarselData(
                journalpost = VarselDataJournalpost(varselUuid.toString(), journalpostId),
                motetidspunkt = motetidspunkt?.let { VarselDataMotetidspunkt(motetidspunkt) }
            ),
            orgnummer = null,
        )
        log.info("Skal sende ${getArbeidstakerHendelseType(varseltype)} til esyfovarselProducer. Journalpostid: $journalpostId")
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    fun lesVarsel(
        personident: Personident,
        varselUuid: UUID,
    ) {
        val hendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_LEST,
            arbeidstakerFnr = personident.value,
            data = VarselData(
                journalpost = VarselDataJournalpost(varselUuid.toString(), null)
            ),
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
