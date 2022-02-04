package no.nav.syfo.dialogmote.tilgang

import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber

class DialogmoteTilgangService(
    private val adressebeskyttelseClient: AdressebeskyttelseClient,
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    suspend fun hasAccessToDialogmotePerson(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val veilederHasAccessToPerson = veilederTilgangskontrollClient.hasAccess(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
        )
        return if (veilederHasAccessToPerson) {
            val personHasAdressebeskyttelse =
                adressebeskyttelseClient.hasAdressebeskyttelse(
                    personIdentNumber = personIdentNumber,
                    callId = callId,
                )
            !personHasAdressebeskyttelse
        } else false
    }

    suspend fun hasAccessToAllDialogmotePersons(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String
    ): Boolean {
        val personListWithVeilederAccess = hasAccessToDialogmotePersonList(
            personIdentNumberList = personIdentNumberList,
            token = token,
            callId = callId,
        )

        return personListWithVeilederAccess.containsAll(personIdentNumberList)
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
            !adressebeskyttelseClient.hasAdressebeskyttelse(personIdentNumber, callId)
        }
    }
}
