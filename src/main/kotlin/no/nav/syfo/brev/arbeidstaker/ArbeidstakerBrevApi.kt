package no.nav.syfo.brev.arbeidstaker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerResponsDTO
import no.nav.syfo.dialogmote.domain.toArbeidstakerBrevDTOList
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import no.nav.syfo.brev.arbeidstaker.domain.PdfContent
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val arbeidstakerBrevApiPath = "/api/v1/arbeidstaker/brev"
const val arbeidstakerBrevApiBrevParam = "brevuuid"
const val arbeidstakerBrevApiLesPath = "/les"
const val arbeidstakerBrevApiResponsPath = "/respons"
const val arbeidstakerBrevApiPdfPath = "/pdf"

fun Route.registerArbeidstakerBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    pdfService: PdfService,
) {
    route(arbeidstakerBrevApiPath) {
        get {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerBrevDTOList = dialogmoteService.getDialogmoteList(
                    personIdentNumber = requestPersonIdent,
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

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerById(
                    moteDeltakerArbeidstakerId = brev.motedeltakerArbeidstakerId
                )

                val hasAccessToBrev = motedeltakerArbeidstaker.personIdent == requestPersonIdent
                if (hasAccessToBrev) {
                    val pdf = pdfService.getPdf(brev.pdfId)
                    call.respond(PdfContent(pdf))
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

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerById(
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

        post("/{$arbeidstakerBrevApiBrevParam}$arbeidstakerBrevApiResponsPath") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")
                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParam])
                val responsDTO = call.receive<ArbeidstakerResponsDTO>()

                val brev = dialogmoteService.getArbeidstakerBrevFromUuid(brevUuid)

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerById(
                    moteDeltakerArbeidstakerId = brev.motedeltakerArbeidstakerId
                )

                val hasAccessToBrev = motedeltakerArbeidstaker.personIdent == requestPersonIdent
                if (hasAccessToBrev) {
                    val updated = dialogmotedeltakerService.updateArbeidstakerBrevWithRespons(
                        brevUuid = brevUuid,
                        svarType = DialogmoteSvarType.valueOf(responsDTO.svarType),
                        svarTekst = responsDTO.svarTekst,
                    )
                    if (updated) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        throw IllegalArgumentException("Response already stored")
                    }
                } else {
                    val accessDeniedMessage = "Denied access to brev with uuid"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not store response for brev with uuid"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
