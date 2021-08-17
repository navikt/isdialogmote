package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

class NarmesteLederService(
    private val narmesteLederClient: NarmesteLederClient
) {
    suspend fun getVirksomhetsnumreOfNarmesteLederByArbeidstaker(
        arbeidstakerIdent: PersonIdentNumber,
        narmesteLederIdent: PersonIdentNumber,
        callId: String
    ): List<Virksomhetsnummer> {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(narmesteLederIdent, callId)
        return aktiveAnsatteRelasjoner.filter { nlrelasjon ->
            nlrelasjon.fnr == arbeidstakerIdent.value
        }.map { relasjon ->
            Virksomhetsnummer(relasjon.orgnummer)
        }.distinctBy { it.value }
    }

    suspend fun getVirksomhetsnumreNarmesteLeder(
        narmesteLederIdent: PersonIdentNumber,
        callId: String
    ): List<Virksomhetsnummer> =
        narmesteLederClient.getAktiveAnsatte(narmesteLederIdent, callId).map { relasjon ->
            Virksomhetsnummer(
                relasjon.orgnummer
            )
        }.distinctBy { it.value }
}
