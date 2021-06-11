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
        return if (veilederHasAccessToPerson) {
            val personHasAdressebeskyttelse =
                adressebeskyttelseClient.hasAdressebeskyttelse(personIdentNumber, token, callId)
            !personHasAdressebeskyttelse
        } else false
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

    suspend fun hasAccessToDialogmoteInnkalling(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {

        return if (hasAccessToDialogmotePerson(personIdentNumber, token, callId)) {
            val kontaktinfo = kontaktinformasjonClient.kontaktinformasjon(personIdentNumber, token, callId)
            val isDigitalVarselAllowed = kontaktinfo?.kontaktinfo?.get(personIdentNumber.value)?.kanVarsles ?: false
            isDigitalVarselAllowed
        } else false
    }
}
