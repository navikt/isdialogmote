package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.registerDialogmoteApi
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService

fun Application.apiModule(
    applicationState: ApplicationState,
    environment: Environment,
    wellKnown: WellKnown
) {
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        wellKnown,
        listOf(environment.loginserviceClientId)
    )
    installStatusPages()

    val moteplanleggerClient = MoteplanleggerClient(
        syfomoteadminBaseUrl = environment.syfomoteadminUrl
    )
    val narmesteLederClient = NarmesteLederClient(
        modiasyforestBaseUrl = environment.modiasyforestUrl
    )

    val dialogmoteService = DialogmoteService(
        moteplanleggerClient = moteplanleggerClient,
        narmesteLederClient = narmesteLederClient
    )

    val adressebeskyttelseClient = AdressebeskyttelseClient(
        syfopersonBaseUrl = environment.syfopersonUrl
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        syfopersonBaseUrl = environment.syfopersonUrl
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        tilgangskontrollBaseUrl = environment.syfotilgangskontrollUrl
    )
    val dialogmoteTilgangService = DialogmoteTilgangService(
        adressebeskyttelseClient = adressebeskyttelseClient,
        kontaktinformasjonClient = kontaktinformasjonClient,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient
    )

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        authenticate {
            registerDialogmoteApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
        }
    }
}
