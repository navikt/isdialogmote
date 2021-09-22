package no.nav.syfo.brev.narmesteleder

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.brev.arbeidstaker.domain.PdfContent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toNarmesteLederBrevDTOList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmesteLederBrevApiBasePath = "/api/v1/narmesteleder/brev"
const val narmesteLederBrevApiLesPath = "/les"
const val narmesteLederBrevApiPdfPath = "/pdf"
const val narmesteLederBrevApiBrevParam = "brevuuid"

fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    narmesteLederAccessService: NarmesteLederAccessService,
) {
    route(narmesteLederBrevApiBasePath) {
        get {
            val callId = getCallId()
            try {
                val narmesteLederPersonIdentNumber = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerPersonIdentNumber = getPersonIdentHeader()?.let { PersonIdentNumber(it) }
                    ?: throw IllegalArgumentException("No $NAV_PERSONIDENT_HEADER provided in request header")

                val moteList = dialogmoteService.getDialogmoteList(personIdentNumber = arbeidstakerPersonIdentNumber)

                val narmesteLederMoter = narmesteLederAccessService.filterMoterByNarmesteLederAccess(
                    arbeidstakerPersonIdentNumber = arbeidstakerPersonIdentNumber,
                    callId = callId,
                    moteList = moteList,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
                )
                call.respond(narmesteLederMoter.toNarmesteLederBrevDTOList())
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
        get("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiPdfPath") {
            val callId = getCallId()

            try {
                val narmesteLederPersonIdentNumber = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val hasAccessToBrev = narmesteLederAccessService.hasAccessToBrev(
                    brev = brev,
                    callId = callId,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
                )

                if (hasAccessToBrev) {
                    call.respond(PdfContent(brev.pdf))
                } else {
                    val accessDeniedMessage = "Denied access to pdf for brev with uuid $brevUuid"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not get pdf for brev with uuid"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
        post("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiLesPath") {
            val callId = getCallId()
            try {
                val narmesteLederPersonIdentNumber = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val hasAccessToBrev = narmesteLederAccessService.hasAccessToBrev(
                    brev = brev,
                    callId = callId,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
                )

                if (hasAccessToBrev) {
                    if (brev.lestDatoArbeidsgiver == null) {
                        dialogmotedeltakerService.updateArbeidsgiverBrevSettSomLest(brevUuid)
                    }
                    call.respond(HttpStatusCode.OK)
                } else {
                    val accessDeniedMessage = "Denied access to brev with uuid"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not set brev with uuid as lest"
                log.warn(
                    "$illegalArgumentMessage: {}, {}",
                    e.message,
                    callIdArgument(callId)
                )
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
