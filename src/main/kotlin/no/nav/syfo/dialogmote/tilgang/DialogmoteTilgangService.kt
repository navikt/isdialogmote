package no.nav.syfo.dialogmote.tilgang

import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber

class DialogmoteTilgangService(
    private val adressebeskyttelseClient: AdressebeskyttelseClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient
) {
    suspend fun hasAccessToDialogmotePerson(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val veilederHasAccessToPerson = veilederTilgangskontrollClient.hasAccess(personIdentNumber, token, callId)
        val personHasAdressebeskyttelse = adressebeskyttelseClient.hasAdressebeskyttelse(personIdentNumber, token, callId)

        return veilederHasAccessToPerson && !personHasAdressebeskyttelse
    }

    suspend fun hasAccessToDialogmotePersonList(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String,
    ): List<PersonIdentNumber> {
        return veilederTilgangskontrollClient.hasAccessToPersonList(
            personIdentNumberList = personIdentNumberList,
            token = token,
            callId = callId,
        ).filter { personIdentNumber ->
            !adressebeskyttelseClient.hasAdressebeskyttelse(personIdentNumber, token, callId)
        }
    }

    suspend fun hasAccessToPlanlagtDialogmoteInnkalling(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val kontaktinfo = kontaktinformasjonClient.kontaktinformasjon(personIdentNumber, token, callId)
        val isDigitalVarselAllowed = kontaktinfo?.kontaktinfo?.get(personIdentNumber.value)?.kanVarsles ?: false

        return hasAccessToDialogmotePerson(personIdentNumber, token, callId) && isDigitalVarselAllowed
    }
}
