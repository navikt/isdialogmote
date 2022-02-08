package no.nav.syfo.util

import io.ktor.application.*
import io.ktor.util.pipeline.*
import no.nav.syfo.application.api.authentication.ForbiddenAccessVeilederException
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.domain.PersonIdentNumber

suspend fun PipelineContext<out Unit, ApplicationCall>.validateVeilederAccess(
    dialogmoteTilgangService: DialogmoteTilgangService,
    personIdentToAccess: PersonIdentNumber,
    action: String,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("No Authorization header supplied")

    val hasVeilederAccess = dialogmoteTilgangService.hasAccessToAllDialogmotePersons(
        callId = callId,
        personIdentNumberList = listOf(personIdentToAccess),
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
