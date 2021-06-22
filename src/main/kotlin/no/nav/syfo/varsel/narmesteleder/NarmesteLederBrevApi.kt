package no.nav.syfo.varsel.arbeidsgiver

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toNarmesteLederBrevDTOList
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmestelederBrevApiPath = "/api/v1/arbeidsgiver/brev"
const val brevUuidParam = "brevuuid"
const val narmestelederBrevApiLesPath = "/les"


fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
) {
    route(narmestelederBrevApiPath) {
        get {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                // TODO: Fix getDialogmoteList så den takler både arbeidsgiver og taker
                val narmesteLederBrevDTOList =
                    dialogmoteService.getDialogmoteList(personIdentNumber = requestPersonIdent)
                        .toNarmesteLederBrevDTOList()
                //2. Finn alle nærmeste ledere til

                call.respond(narmesteLederBrevDTOList)
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }

        }
    }
}
