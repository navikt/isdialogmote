package no.nav.syfo.brev.narmesteleder

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.brev.arbeidstaker.domain.PdfContent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toNarmesteLederBrevDTOList
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getPersonIdentHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmesteLederBrevApiBasePath = "/api/v1/narmesteleder/brev"
const val narmesteLederBrevApiLesPath = "/les"
const val narmesteLederBrevApiPdfPath = "/pdf"
const val narmesteLederBrevApiBrevParam = "brevuuid"

fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    narmesteLederService: NarmesteLederService,
) {
    route(narmesteLederBrevApiBasePath) {
        get {
            val callId = getCallId()
            try {
                val narmesteLederIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerIdent = getPersonIdentHeader()?.let { PersonIdentNumber(it) }
                    ?: throw IllegalArgumentException("No $NAV_PERSONIDENT_HEADER provided in request header")

                val moter = dialogmoteService.getDialogmoteList(personIdentNumber = arbeidstakerIdent)
                val virksomhetsnumre = narmesteLederService.getVirksomhetsnumreOfNarmesteLederByArbeidstaker(
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
        get("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiPdfPath") {
            val callId = getCallId()

            try {
                val personIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val virksomhetnumre = narmesteLederService.getVirksomhetsnumreNarmesteLeder(personIdent, callId)

                val dialogmoteDeltagerArbeidsgiver =
                    dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(brev.motedeltakerArbeidsgiverId)

                val hasAccessToBrev =
                    virksomhetnumre.contains(dialogmoteDeltagerArbeidsgiver.virksomhetsnummer)

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
                val personIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val virksomhetnumre = narmesteLederService.getVirksomhetsnumreNarmesteLeder(personIdent, callId)

                val dialogmoteDeltagerArbeidsgiver =
                    dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(brev.motedeltakerArbeidsgiverId)

                val hasAccessToBrev =
                    virksomhetnumre.contains(dialogmoteDeltagerArbeidsgiver.virksomhetsnummer)

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
