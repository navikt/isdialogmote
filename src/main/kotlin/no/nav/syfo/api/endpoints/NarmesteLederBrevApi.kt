package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.authentication.personIdent
import no.nav.syfo.api.callIdArgument
import no.nav.syfo.api.getBearerHeader
import no.nav.syfo.api.getCallId
import no.nav.syfo.api.getPersonIdentHeader
import no.nav.syfo.application.NarmesteLederAccessService
import no.nav.syfo.domain.PdfContent
import no.nav.syfo.domain.NarmesteLederResponsDTO
import no.nav.syfo.infrastructure.database.dialogmote.DialogmoteService
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerService
import no.nav.syfo.infrastructure.database.dialogmote.PdfService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.toNarmesteLederBrevDTOList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmesteLederBrevApiBasePath = "/api/v2/narmesteleder/brev"
const val narmesteLederBrevApiLesPath = "/les"
const val narmesteLederBrevApiPdfPath = "/pdf"
const val narmesteLederBrevApiResponsPath = "/respons"
const val narmesteLederBrevApiBrevParam = "brevuuid"

fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    narmesteLederAccessService: NarmesteLederAccessService,
    pdfService: PdfService,
) {
    route(narmesteLederBrevApiBasePath) {
        get {
            val callId = getCallId()
            try {
                val token = this.getBearerHeader()
                    ?: throw IllegalArgumentException("No token found")
                val narmesteLederPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerPersonIdent = getPersonIdentHeader()?.let { PersonIdent(it) }
                    ?: throw IllegalArgumentException("No $NAV_PERSONIDENT_HEADER provided in request header")

                val moteList = dialogmoteService.getDialogmoteList(personIdent = arbeidstakerPersonIdent)
                    .filter { dialogmote ->
                        dialogmote.status != DialogmoteStatus.LUKKET
                    }

                val narmesteLederMoter = narmesteLederAccessService.filterMoterByNarmesteLederAccess(
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    callId = callId,
                    moteList = moteList,
                    narmesteLederPersonIdent = narmesteLederPersonIdent,
                    tokenx = token,
                )

                val removedExpiredBrev = narmesteLederAccessService.removeExpiredBrevInDialogmoter(
                    moteList = narmesteLederMoter,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdent,
                    arbeidstakerPersonIdentNumber = arbeidstakerPersonIdent,
                    tokenx = token,
                    callId = callId
                )

                call.respond(removedExpiredBrev.toNarmesteLederBrevDTOList())
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
        get("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiPdfPath") {
            val callId = getCallId()

            try {
                val token = this.getBearerHeader()
                    ?: throw IllegalArgumentException("No token found")
                val narmesteLederPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val hasAccessToBrev = narmesteLederAccessService.hasAccessToBrev(
                    brev = brev,
                    callId = callId,
                    tokenx = token,
                    narmesteLederPersonIdent = narmesteLederPersonIdent,
                )

                val isBrevExpired = narmesteLederAccessService.isBrevExpired(
                    brev = brev,
                    callId = callId,
                    tokenx = token,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdent
                )

                if (hasAccessToBrev && brev.pdfId != null && !isBrevExpired) {
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
        post("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiLesPath") {
            val callId = getCallId()
            try {
                val token = this.getBearerHeader()
                    ?: throw IllegalArgumentException("No token found")
                val narmesteLederPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val hasAccessToBrev = narmesteLederAccessService.hasAccessToBrev(
                    brev = brev,
                    callId = callId,
                    tokenx = token,
                    narmesteLederPersonIdent = narmesteLederPersonIdent,
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
        post("/{$narmesteLederBrevApiBrevParam}$narmesteLederBrevApiResponsPath") {
            val callId = getCallId()
            try {
                val token = this.getBearerHeader()
                    ?: throw IllegalArgumentException("No token found")
                val narmesteLederPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")
                val brevUuid = UUID.fromString(call.parameters[narmesteLederBrevApiBrevParam])
                val responsDTO = call.receive<NarmesteLederResponsDTO>() // TODO: bytt navn, dette er en post request (med et svar i)

                val brev = dialogmoteService.getNarmesteLederBrevFromUuid(brevUuid)

                val hasAccessToBrev = narmesteLederAccessService.hasAccessToBrev(
                    brev = brev,
                    callId = callId,
                    tokenx = token,
                    narmesteLederPersonIdent = narmesteLederPersonIdent,
                )
                if (hasAccessToBrev) {
                    val narmesteLederSvar = DialogmoteSvarType.valueOf(responsDTO.svarType)
                    val updated = dialogmotedeltakerService.updateArbeidsgiverBrevWithRespons(
                        brevUuid = brevUuid,
                        svarType = narmesteLederSvar,
                        svarTekst = responsDTO.svarTekst,
                    )
                    if (updated) {
                        dialogmoteService.publishNarmesteLederSvarVarselHendelse(
                            brev = brev,
                            narmesteLederSvar = narmesteLederSvar,
                            narmesteLederPersonIdent = narmesteLederPersonIdent,
                        )
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
