package no.nav.syfo.api.endpoints

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.registerMotebehovApi() {
    route("/api/motebehov") {

        // Endepunkt som henter motebehov for en personident (brukes i syfomodiaperson)
        get {
            // TODO: validateVeilederAccess
            // hent motebehov fra syfomotebehov for personident i requesten
            // hent ut info om avvent via AvventService
            // Legg sammen infoen og returner i response
            call.respond(HttpStatusCode.OK)
        }

        // Batch endepunkt som syfooversiktsrv kan bruke for info om motebehov,
        // inkludert om det skal  avventes
        get("/batch") {
            // TODO: validateVeilederAccess
            // for hver ident i requesten, hent motebehov fra syfo modia
            // hent ut info om avvent via AvventService
            // Legg sammen infoen og returner i response
            call.respond(HttpStatusCode.OK)
        }
    }
}
