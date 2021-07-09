package no.nav.syfo.brev.narmesteleder

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.application.api.authentication.personIdentAT
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

                val arbeidstakerIdent = getPersonIdentHeader()?.let { it -> PersonIdentNumber(it) }
                    ?: throw IllegalArgumentException("No $NAV_PERSONIDENT_HEADER provided in request header")

                val moter = dialogmoteService.getDialogmoteList(personIdentNumber = arbeidstakerIdent)
                val virksomhetsnummer = narmesteLederService.getVirksomhetsnummer(
                    arbeidstakerIdent,
                    narmesteLederIdent,
                    callId
                )
                val narmesteLederMoter =
                    moter.filter { it.arbeidsgiver.virksomhetsnummer.value == virksomhetsnummer?.value }

                call.respond(narmesteLederMoter.toNarmesteLederBrevDTOList())
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
