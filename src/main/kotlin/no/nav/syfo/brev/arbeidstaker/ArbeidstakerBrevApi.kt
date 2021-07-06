package no.nav.syfo.brev.arbeidstaker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toArbeidstakerBrevDTOList
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import no.nav.syfo.brev.arbeidstaker.domain.PdfContent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val arbeidstakerBrevApiPath = "/api/v1/arbeidstaker/brev"
const val arbeidstakerBrevApiBrevParam = "brevuuid"
const val arbeidstakerBrevApiLesPath = "/les"
const val arbeidstakerBrevApiPdfPath = "/pdf"

fun Route.registerArbeidstakerBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
) {
    route(arbeidstakerBrevApiPath) {
        get {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerBrevDTOList = dialogmoteService.getDialogmoteList(
                    personIdentNumber = requestPersonIdent, callId
                ).toArbeidstakerBrevDTOList()
                call.respond(arbeidstakerBrevDTOList)
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve list of brev"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }

        get("/{$arbeidstakerBrevApiBrevParam}$arbeidstakerBrevApiPdfPath") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParam])

                val brev = dialogmoteService.getArbeidstakerBrevFromUuid(brevUuid)

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerFromId(
                    moteDeltakerArbeidstakerId = brev.motedeltakerArbeidstakerId
                )

                val hasAccessToBrev = motedeltakerArbeidstaker.personIdent == requestPersonIdent
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

        post("/{$arbeidstakerBrevApiBrevParam}$arbeidstakerBrevApiLesPath") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParam])

                val brev = dialogmoteService.getArbeidstakerBrevFromUuid(brevUuid)

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerFromId(
                    moteDeltakerArbeidstakerId = brev.motedeltakerArbeidstakerId
                )

                val hasAccessToBrev = motedeltakerArbeidstaker.personIdent == requestPersonIdent
                if (hasAccessToBrev) {
                    if (brev.lestDatoArbeidstaker == null) {
                        dialogmotedeltakerService.updateArbeidstakerBrevSettSomLest(
                            personIdentNumber = requestPersonIdent,
                            dialogmotedeltakerArbeidstakerUuid = motedeltakerArbeidstaker.uuid,
                            brevUuid = brevUuid,
                        )
                    }
                    call.respond(HttpStatusCode.OK)
                } else {
                    val accessDeniedMessage = "Denied access to brev with uuid"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not set brev with uuid as lest"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
