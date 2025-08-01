package no.nav.syfo.infrastructure.database.dialogmote.tilgang

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
