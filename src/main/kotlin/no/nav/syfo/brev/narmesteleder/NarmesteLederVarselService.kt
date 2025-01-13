package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.brev.esyfovarsel.*
import no.nav.syfo.client.narmesteleder.*
import no.nav.syfo.dialogmote.domain.*
import java.time.LocalDateTime

class NarmesteLederVarselService(
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    fun sendVarsel(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType,
        motetidspunkt: LocalDateTime?,
    ) {
        val hendelse = NarmesteLederHendelse(
            type = getNaermesteLederVarselType(varseltype),
            data = VarselData(
                narmesteLeder = VarselDataNarmesteLeder(narmesteLeder.narmesteLederNavn),
                motetidspunkt = motetidspunkt?.let { VarselDataMotetidspunkt(it) }
            ),
            narmesteLederFnr = narmesteLeder.narmesteLederPersonIdentNumber,
            arbeidstakerFnr = narmesteLeder.arbeidstakerPersonIdentNumber,
            orgnummer = narmesteLeder.virksomhetsnummer
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
