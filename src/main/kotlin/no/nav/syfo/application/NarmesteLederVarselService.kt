package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselData
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselDataDialogmoteSvar
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselDataMotetidspunkt
import no.nav.syfo.infrastructure.kafka.esyfovarsel.VarselDataNarmesteLeder
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

    fun sendNarmesteLederSvarVarselHendelse(
        narmesteLederSvar: DialogmoteSvarType,
        narmesteLederPersonIdent: PersonIdent,
        arbeidstakerPersonIdent: PersonIdent,
        virksomhetsnummer: Virksomhetsnummer,
    ) {
        esyfovarselProducer.sendVarselToEsyfovarsel(
            NarmesteLederHendelse(
                type = HendelseType.NL_DIALOGMOTE_SVAR,
                data = VarselData(
                    dialogmoteSvar = VarselDataDialogmoteSvar(
                        svar = narmesteLederSvar,
                    )
                ),
                narmesteLederFnr = narmesteLederPersonIdent.value,
                arbeidstakerFnr = arbeidstakerPersonIdent.value,
                orgnummer = virksomhetsnummer.value,
            )
        )
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
