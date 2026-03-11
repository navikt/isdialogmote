package no.nav.syfo.application

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient

class DialogmoteTilgangService(
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    suspend fun hasFullTilgangAndPersonAccess(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): Boolean = coroutineScope {
        val hasFullTilgang = async {
            veilederTilgangskontrollClient.hasSyfoFullTilgang(
                token = token,
                callId = callId,
            )
        }

        val hasAccessToAllPersons = async {
            hasAccessToAllDialogmotePersons(
                personIdentList = personIdentList,
                token = token,
                callId = callId,
            )
        }

        hasFullTilgang.await() && hasAccessToAllPersons.await()
    }

    suspend fun hasAccessToAllDialogmotePersons(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
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
    ): List<PersonIdent> =
        veilederTilgangskontrollClient.hasAccessToPersonList(
            personIdentList = personIdentList,
            token = token,
            callId = callId,
        )
}
