package no.nav.syfo.varsel.arbeidsgiver

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.api.authentication.personIdent
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.varsel.narmesteleder.NarmesteLederBrevService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val narmestelederBrevApiPath = "/api/v1/arbeidsgiver/brev"
const val brevUuidParam = "brevuuid"
const val narmestelederBrevApiLesPath = "/les"


fun Route.registerNarmestelederBrevApi(
    dialogmoteService: DialogmoteService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    narmesteLederBrevService: NarmesteLederBrevService,
) {
    route(narmestelederBrevApiPath) {
        get {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("No Authorization header supplied")
            try {
                val narmesteLederIdent = call.personIdent()
                    ?: throw IllegalArgumentException("No PersonIdent found in token")

                val arbeidstakerIdent = PersonIdentNumber(call.parameters["personIdentAT"]!!);

                val moter = dialogmoteService.getDialogmoteList(personIdentNumber = arbeidstakerIdent)
                narmesteLederBrevService.filterByNarmesteLeder(moter, narmesteLederIdent, callId, token)



                //1. Finn alle dialogmøter til arbeidstaker
                //2. Finn alle nærmeste ledere til den ansatte
                //3. Koble nærmeste leder på møtet via orgnummer
                //4. Fjerne møter som ikke har riktig nærmeste leder basert på fnr til NL

//                call.respond(narmesteLederBrevDTOList)
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve BrevList"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(callId))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }

        }
    }
}
