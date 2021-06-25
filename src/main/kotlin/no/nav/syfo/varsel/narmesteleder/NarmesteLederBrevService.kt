package no.nav.syfo.varsel.narmesteleder

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.Dialogmote
import no.nav.syfo.domain.PersonIdentNumber

class NarmesteLederBrevService(
    private val narmesteLederClient: NarmesteLederClient
) {

    suspend fun filterByNarmesteLeder(
        moter: List<Dialogmote>,
        narmesteLederIdent: PersonIdentNumber,
        token: String,
        callId: String
    ): List<Dialogmote> {
        val personIdentNumber = moter.first().arbeidstaker.personIdent

        // alle nærmeste leder for sykmeldt
        val narmesteLedere = narmesteLederClient.narmesteLedere(personIdentNumber, token, callId)

        return moter.filter {
            1 == 1
        }
        //1. Finn alle dialogmøter til arbeidstaker
        //2. Finn alle nærmeste ledere til den ansatte
        //3. Koble nærmeste leder på møtet via orgnummer
        //4. Fjerne møter som ikke har riktig nærmeste leder basert på fnr til NL
    }
}
