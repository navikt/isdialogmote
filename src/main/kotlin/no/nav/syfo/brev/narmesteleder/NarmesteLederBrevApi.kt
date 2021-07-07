package no.nav.syfo.brev.narmesteleder

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.domain.toNarmesteLederBrevDTOList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getPersonIdentHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmestelederBrevApiPath = "/api/v1/narmesteleder/brev"

fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    narmesteLederService: NarmesteLederService,
) {
    route(narmestelederBrevApiPath) {
        get {
            val callId = getCallId()
            try {
                val narmesteLederIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerIdent = getPersonIdentHeader()?.let { PersonIdentNumber(it) }
                    ?: throw IllegalArgumentException("No $NAV_PERSONIDENT_HEADER provided in request header")

                val moter = dialogmoteService.getDialogmoteList(personIdentNumber = arbeidstakerIdent)
                val virksomhetsnumre = narmesteLederService.getVirksomhetsnumreOfNarmestelederByArbeidstaker(
                    arbeidstakerIdent,
                    narmesteLederIdent,
                    callId
                )
                val narmesteLederMoter = moter.filter { virksomhetsnumre.contains(it.arbeidsgiver.virksomhetsnummer) }

                call.respond(narmesteLederMoter.toNarmesteLederBrevDTOList())
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
