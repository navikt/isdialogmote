package no.nav.syfo.brev.narmesteleder.domain

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

class NarmesteLederService(
    private val narmesteLederClient: NarmesteLederClient
) {

    suspend fun getVirksomhetsnummer(
        arbeidstakerIdent: PersonIdentNumber,
        narmesteLederIdent: PersonIdentNumber,
        callId: String
    ): Virksomhetsnummer? {
        val narmesteLedere = narmesteLederClient.narmesteLedere(arbeidstakerIdent, callId)
        val narmesteLeder = narmesteLedere.find { nl -> nl.narmesteLederFnr == narmesteLederIdent.value }

        return if (narmesteLeder != null) Virksomhetsnummer(narmesteLeder.orgnummer) else null
    }
}
