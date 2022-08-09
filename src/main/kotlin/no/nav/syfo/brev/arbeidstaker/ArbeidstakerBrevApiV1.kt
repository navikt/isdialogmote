package no.nav.syfo.brev.arbeidstaker

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

const val arbeidstakerBrevApiPathV1 = "/api/v1/arbeidstaker/brev"
const val arbeidstakerBrevApiBrevParamV1 = "brevuuid"
const val arbeidstakerBrevApiLesPathV1 = "/les"
const val arbeidstakerBrevApiResponsPathV1 = "/respons"
const val arbeidstakerBrevApiPdfPathV1 = "/pdf"

fun Route.registerArbeidstakerBrevApiV1(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    pdfService: PdfService,
) {
    route(arbeidstakerBrevApiPathV1) {
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

        get("/{$arbeidstakerBrevApiBrevParamV1}$arbeidstakerBrevApiPdfPathV1") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParamV1])

                val brev = dialogmoteService.getArbeidstakerBrevFromUuid(brevUuid)

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerById(
                    moteDeltakerArbeidstakerId = brev.motedeltakerArbeidstakerId
                )

                val hasAccessToBrev = motedeltakerArbeidstaker.personIdent == requestPersonIdent
                if (hasAccessToBrev && brev.pdfId != null) {
                    val pdf = pdfService.getPdf(brev.pdfId!!)
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

        post("/{$arbeidstakerBrevApiBrevParamV1}$arbeidstakerBrevApiLesPathV1") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParamV1])

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

        post("/{$arbeidstakerBrevApiBrevParamV1}$arbeidstakerBrevApiResponsPathV1") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")
                val brevUuid = UUID.fromString(call.parameters[arbeidstakerBrevApiBrevParamV1])
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