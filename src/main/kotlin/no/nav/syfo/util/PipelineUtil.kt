package no.nav.syfo.util

import io.ktor.server.routing.*
import no.nav.syfo.application.api.authentication.ForbiddenAccessVeilederException
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdent

suspend fun RoutingContext.validateVeilederAccess(
    dialogmoteTilgangService: DialogmoteTilgangService,
    personIdentToAccess: PersonIdent,
    action: String,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("No Authorization header supplied")

    val hasVeilederAccess = dialogmoteTilgangService.hasAccessToAllDialogmotePersons(
        callId = callId,
        personIdentList = listOf(personIdentToAccess),
        token = token,
    )
    if (hasVeilederAccess) {
        requestBlock()
    } else {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}
