package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient

class DialogmoteTilgangService(
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    suspend fun hasAccessToAllDialogmotePersons(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): Boolean {
        personIdentList.map {
            if (!hasAccessToDialogmotePerson(it, token, callId)) {
                return false
            }
        }
        return true
    }

    suspend fun hasAccessToDialogmotePerson(
        personident: PersonIdent,
        token: String,
        callId: String,
    ): Boolean =
        veilederTilgangskontrollClient.hasAccessToPerson(
            personident = personident,
            token = token,
            callId = callId,
        )

    suspend fun hasAccessToDialogmotePersonList(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): List<PersonIdent> =
        veilederTilgangskontrollClient.hasAccessToPersonList(
            personIdentList = personIdentList,
            token = token,
            callId = callId,
        )
}
