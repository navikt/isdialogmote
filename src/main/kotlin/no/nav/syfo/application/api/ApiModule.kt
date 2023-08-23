package no.nav.syfo.application.api

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.registerArbeidstakerBrevApi
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.brev.narmesteleder.registerNarmestelederBrevApi
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteActionsApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteEnhetApiV2
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService

fun Application.apiModule(
    applicationState: ApplicationState,
    esyfovarselProducer: EsyfovarselProducer,
    behandlerVarselService: BehandlerVarselService,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeilederV2: WellKnown,
    cache: RedisStore,
    altinnSoapClient: ICorrespondenceAgencyExternalBasic,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.tokenxClientId),
                jwtIssuerType = JwtIssuerType.SELVBETJENING,
                wellKnown = wellKnownSelvbetjening,
            ),
            JwtIssuer(
                acceptedAudienceList = listOf(environment.aadAppClient),
                jwtIssuerType = JwtIssuerType.VEILEDER_V2,
                wellKnown = wellKnownVeilederV2,
            ),
        ),
    )
    installStatusPages()

    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        redisStore = cache,
    )
    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenxClientId,
        tokenxEndpoint = environment.tokenxEndpoint,
        tokenxPrivateJWK = environment.tokenxPrivateJWK,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
        redisStore = cache,
    )
    val adressebeskyttelseClient = AdressebeskyttelseClient(
        pdlClient = pdlClient,
        cache = cache,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdV2Client = azureAdV2Client,
        syfobehandlendeenhetBaseUrl = environment.syfobehandlendeenhetUrl,
        syfobehandlendeenhetClientId = environment.syfobehandlendeenhetClientId,
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        azureAdV2Client = azureAdV2Client,
        cache = cache,
        clientId = environment.krrClientId,
        baseUrl = environment.krrUrl,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleBaseUrl = environment.isoppfolgingstilfelleUrl,
        isoppfolgingstilfelleClientId = environment.isoppfolgingstilfelleClientId,
        cache = cache,
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.isdialogmotepdfgenUrl
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdV2Client = azureAdV2Client,
        syfotilgangskontrollClientId = environment.syfotilgangskontrollClientId,
        tilgangskontrollBaseUrl = environment.syfotilgangskontrollUrl
    )

    val dialogmoteTilgangService = DialogmoteTilgangService(
        adressebeskyttelseClient = adressebeskyttelseClient,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
    )

    val arbeidstakerVarselService = ArbeidstakerVarselService(
        esyfovarselProducer = esyfovarselProducer,
    )

    val narmesteLederVarselService = NarmesteLederVarselService(
        esyfovarselProducer = esyfovarselProducer,
    )

    val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
    )

    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = environment.narmestelederUrl,
        narmestelederClientId = environment.narmestelederClientId,
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        cache = cache,
    )
    val pdfService = PdfService(
        database = database,
    )

    val altinnClient = AltinnClient(
        username = environment.altinnUsername,
        password = environment.altinnPassword,
        altinnSoapClient = altinnSoapClient,
    )

    val varselService = VarselService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        narmesteLederVarselService = narmesteLederVarselService,
        behandlerVarselService = behandlerVarselService,
        altinnClient = altinnClient,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        isAltinnSendingEnabled = environment.altinnSendingEnabled,
    )

    val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )

    val dialogmoterelasjonService = DialogmoterelasjonService(
        dialogmotedeltakerService = dialogmotedeltakerService,
        database = database,
    )

    val dialogmoteService = DialogmoteService(
        database = database,
        dialogmotedeltakerService = dialogmotedeltakerService,
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        behandlendeEnhetClient = behandlendeEnhetClient,
        narmesteLederClient = narmesteLederClient,
        pdfGenClient = pdfGenClient,
        kontaktinformasjonClient = kontaktinformasjonClient,
        varselService = varselService,
        pdlClient = pdlClient,
    )

    val narmesteLederTilgangService = NarmesteLederAccessService(
        dialogmotedeltakerService = dialogmotedeltakerService,
        narmesteLederClient = narmesteLederClient,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )

    routing {
        registerPodApi(applicationState, database)
        registerPrometheusApi()
        authenticate(JwtIssuerType.VEILEDER_V2.name) {
            registerDialogmoteEnhetApiV2(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
            registerDialogmoteApiV2(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteActionsApiV2(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService
            )
        }
        authenticate(JwtIssuerType.SELVBETJENING.name) {
            registerArbeidstakerBrevApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
                pdfService = pdfService,
                pdlClient = pdlClient,
            )
            registerNarmestelederBrevApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
                narmesteLederAccessService = narmesteLederTilgangService,
                pdfService = pdfService,
            )
        }
    }
}
