package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.esyfovarsel.EsyfovarselHendelseType
import no.nav.syfo.brev.esyfovarsel.EsyfovarselNarmesteLederHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType

class NarmesteLederVarselService(
    private val mqSender: MQSenderInterface,
    private val dineSykmeldteVarselProducer: DineSykmeldteVarselProducer,
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    fun sendVarsel(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType
    ) {
        val hendelse = EsyfovarselNarmesteLederHendelse(
            getNaermesteLederVarselType(varseltype),
            null,
            narmesteLeder.narmesteLederPersonIdentNumber,
            narmesteLeder.narmesteLederNavn,
            narmesteLeder.arbeidstakerPersonIdentNumber,
            narmesteLeder.virksomhetsnummer
        )
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    private fun getNaermesteLederVarselType(motedeltakerVarselType: MotedeltakerVarselType): EsyfovarselHendelseType {
        return when (motedeltakerVarselType) {
            MotedeltakerVarselType.INNKALT -> EsyfovarselHendelseType.NL_DIALOGMOTE_INNKALT
            MotedeltakerVarselType.AVLYST -> EsyfovarselHendelseType.NL_DIALOGMOTE_AVLYST
            MotedeltakerVarselType.NYTT_TID_STED -> EsyfovarselHendelseType.NL_DIALOGMOTE_NYTT_TID_STED
            MotedeltakerVarselType.REFERAT -> EsyfovarselHendelseType.NL_DIALOGMOTE_REFERAT
        }
    }
}
