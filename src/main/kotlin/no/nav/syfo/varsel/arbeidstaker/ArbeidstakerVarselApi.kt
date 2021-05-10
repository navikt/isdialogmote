package no.nav.syfo.varsel.arbeidstaker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toArbeidstakerVarselDTOList
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getCallId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val arbeidstakerVarselApiPath = "/api/v1/arbeidstaker/varsel"
const val arbeidstakerVarselApiVarselParam = "varseluuid"
const val arbeidstakerVarselApiLesPath = "/les"

fun Route.registerArbeidstakerVarselApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
) {
    route(arbeidstakerVarselApiPath) {
        get {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerVarselDTOList = dialogmoteService.getDialogmoteList(
                    personIdentNumber = requestPersonIdent,
                ).toArbeidstakerVarselDTOList()
                call.respond(arbeidstakerVarselDTOList)
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve VarselList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }

        post("/{$arbeidstakerVarselApiVarselParam}$arbeidstakerVarselApiLesPath") {
            val callId = getCallId()
            try {
                val requestPersonIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val varselUuid = UUID.fromString(call.parameters[arbeidstakerVarselApiVarselParam])

                val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(
                    personIdentNumber = requestPersonIdent
                )
                val motedeltakerArbeidstakerVarsel = motedeltakerArbeidstaker.varselList.find { varsel ->
                    varsel.uuid == varselUuid
                } ?: throw Exception("No Varsel found for PersonIdent with uuid=$varselUuid")

                val hasAccessToVarsel = true
                if (hasAccessToVarsel) {
                    dialogmotedeltakerService.lesDialogmotedeltakerArbeidstakerVarsel(
                        personIdentNumber = requestPersonIdent,
                        dialogmotedeltakerArbeidstakerUuid = motedeltakerArbeidstaker.uuid,
                        dialogmotedeltakerArbeidstakerVarselUuid = motedeltakerArbeidstakerVarsel.uuid,
                    )
                    call.respond(HttpStatusCode.OK)
                } else {
                    val accessDeniedMessage = "Denied access to Les Varsel for varselUUID"
                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not Les Varsel for varselUUID"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
