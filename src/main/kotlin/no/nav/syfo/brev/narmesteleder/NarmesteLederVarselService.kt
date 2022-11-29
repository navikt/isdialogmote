package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.brev.esyfovarsel.HendelseType
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType

class NarmesteLederVarselService(
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    fun sendVarsel(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType
    ) {
        val hendelse = NarmesteLederHendelse(
            getNaermesteLederVarselType(varseltype),
            null,
            narmesteLeder.narmesteLederPersonIdentNumber,
            narmesteLeder.narmesteLederNavn,
            narmesteLeder.arbeidstakerPersonIdentNumber,
            narmesteLeder.virksomhetsnummer
        )
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }

    private fun getNaermesteLederVarselType(motedeltakerVarselType: MotedeltakerVarselType): HendelseType {
        return when (motedeltakerVarselType) {
            MotedeltakerVarselType.INNKALT -> HendelseType.NL_DIALOGMOTE_INNKALT
            MotedeltakerVarselType.AVLYST -> HendelseType.NL_DIALOGMOTE_AVLYST
            MotedeltakerVarselType.NYTT_TID_STED -> HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
            MotedeltakerVarselType.REFERAT -> HendelseType.NL_DIALOGMOTE_REFERAT
        }
    }
}
