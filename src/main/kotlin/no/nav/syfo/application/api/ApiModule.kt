package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.api.*
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.varsel.arbeidstaker.registerArbeidstakerVarselApi

fun Application.apiModule(
    applicationState: ApplicationState,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeileder: WellKnown,
) {
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                accectedAudienceList = environment.loginserviceIdportenAudience,
                jwtIssuerType = JwtIssuerType.selvbetjening,
                wellKnown = wellKnownSelvbetjening,
            ),
            JwtIssuer(
                accectedAudienceList = listOf(environment.loginserviceClientId),
                jwtIssuerType = JwtIssuerType.veileder,
                wellKnown = wellKnownVeileder,
            )
        ),
    )
    installStatusPages()

    val moteplanleggerClient = MoteplanleggerClient(
        syfomoteadminBaseUrl = environment.syfomoteadminUrl
    )
    val narmesteLederClient = NarmesteLederClient(
        modiasyforestBaseUrl = environment.modiasyforestUrl
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

    val arbeidstakerVarselService = ArbeidstakerVarselService(
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        dialogmoteArbeidstakerUrl = environment.dialogmoteArbeidstakerUrl,
        serviceuserUsername = environment.serviceuserUsername,
    )

    val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
    )

    val dialogmoteService = DialogmoteService(
        database = database,
        arbeidstakerVarselService = arbeidstakerVarselService,
        dialogmotedeltakerService = dialogmotedeltakerService,
        moteplanleggerClient = moteplanleggerClient,
        narmesteLederClient = narmesteLederClient
    )

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        authenticate(JwtIssuerType.veileder.name) {
            registerDialogmoteApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteActionsApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteEnhetApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
        }
        authenticate(JwtIssuerType.selvbetjening.name) {
            registerArbeidstakerVarselApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
            )
        }
    }
}
