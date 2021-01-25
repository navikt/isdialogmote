package no.nav.syfo.application.dialogmote

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Routing.registerDialogmoteApi() {
    route("/api/v1/dialogmote") {
        get("/personident") {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
