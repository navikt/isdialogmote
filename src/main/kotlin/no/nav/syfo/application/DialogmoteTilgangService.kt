package no.nav.syfo.application

import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangEnhetClient
import no.nav.syfo.tilgangskontroll.client.VeilederTilgangskontrollClient

class DialogmoteTilgangService(
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    private val veilederTilgangEnhetClient: VeilederTilgangEnhetClient,
) {
    suspend fun hasAccessToAllDialogmotePersons(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): Boolean {
        // TODO: Her skal vi også sjekke full tilgang (så kan *ikke* bruke filterAccessToDialogmotePersonList)
        return personIdentList.all { hasAccessToDialogmotePerson(it, token, callId) }
    }

    suspend fun hasAccessToDialogmotePerson(
        personident: PersonIdent,
        token: String,
        callId: String,
    ): Boolean =
        veilederTilgangskontrollClient.hasAccess(
            callId = callId,
            personident = personident.value,
            token = token,
        )

    suspend fun filterAccessToDialogmotePersonList(
        personIdentList: List<PersonIdent>,
        token: String,
        callId: String,
    ): List<PersonIdent> =
        veilederTilgangskontrollClient.veilederPersonerAccess(
            personidenter = personIdentList.map { it.value },
            token = token,
            callId = callId,
        )?.map { PersonIdent(it) } ?: emptyList()

    suspend fun hasAccessToEnhet(
        enhetNr: EnhetNr,
        token: String,
        callId: String,
    ): Boolean =
        veilederTilgangEnhetClient.hasAccessToEnhet(
            enhetNr = enhetNr,
            token = token,
            callId = callId,
        )
}

