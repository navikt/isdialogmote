package no.nav.syfo.dialogmote.tilgang

import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent

class DialogmoteTilgangService(
    private val adressebeskyttelseClient: AdressebeskyttelseClient,
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    private val kode6Enabled: Boolean,
) {
    suspend fun hasAccessToAllDialogmotePersons(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String
    ): Boolean {
        val personListWithVeilederAccess = hasAccessToDialogmotePersonList(
            personIdentList = personIdentList,
            token = token,
            callId = callId,
        )

        return personListWithVeilederAccess.containsAll(personIdentList)
    }

    suspend fun hasAccessToDialogmotePersonList(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): List<PersonIdent> {
        val personIdentList = veilederTilgangskontrollClient.hasAccessToPersonList(
            personIdentList = personIdentList,
            token = token,
            callId = callId,
        )
        return if (kode6Enabled) personIdentList else personIdentList.filter { personIdent ->
            !adressebeskyttelseClient.hasAdressebeskyttelse(personIdent, callId)
        }
    }
}
