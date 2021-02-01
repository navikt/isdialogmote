package no.nav.syfo.dialogmote.tilgang

import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber

class DialogmoteTilgangService(
    private val adressebeskyttelseClient: AdressebeskyttelseClient,
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient
) {
    suspend fun hasAccessToDialogmote(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val veilederHasAccessToPerson = veilederTilgangskontrollClient.hasAccess(personIdentNumber, token, callId)
        val personHasAdressebeskyttelse = adressebeskyttelseClient.hasAdressebeskyttelse(personIdentNumber, token, callId)
        return veilederHasAccessToPerson && !personHasAdressebeskyttelse
    }
}
