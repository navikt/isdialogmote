package no.nav.syfo.dialogmote

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getPersonIdentHeader

fun Route.registerDialogmoteApi() {
    route("/api/v1/dialogmote") {
        get("/personident") {
            val callId = getCallId()

            val personIdent = getPersonIdentHeader()
                ?: call.respond(HttpStatusCode.BadRequest, "No PersonIdent supplied")

            val token = getBearerHeader()
                ?: call.respond(HttpStatusCode.BadRequest, "No Authorization header supplied")

            val dialogmoteList = emptyList<Any>()
            call.respond(dialogmoteList)
        }
    }
}
