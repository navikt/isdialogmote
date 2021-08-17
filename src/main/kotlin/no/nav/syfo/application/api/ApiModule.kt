package no.nav.syfo.application.api

import io.ktor.application.Application
import io.ktor.auth.authenticate
import io.ktor.routing.routing
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.JwtIssuer
import no.nav.syfo.application.api.authentication.JwtIssuerType
import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.application.api.authentication.installCallId
import no.nav.syfo.application.api.authentication.installContentNegotiation
import no.nav.syfo.application.api.authentication.installJwtAuthentication
import no.nav.syfo.application.api.authentication.installMetrics
import no.nav.syfo.application.api.authentication.installStatusPages
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.registerArbeidstakerBrevApi
import no.nav.syfo.brev.narmesteleder.NarmesteLederService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.brev.narmesteleder.registerNarmestelederBrevApi
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.api.v1.registerDialogmoteActionsApi
import no.nav.syfo.dialogmote.api.v1.registerDialogmoteApi
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteActionsApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteEnhetApiV2
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

fun Application.apiModule(
    applicationState: ApplicationState,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    database: DatabaseInterface,
    mqSender: MQSenderInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeileder: WellKnown,
    wellKnownVeilederV2: WellKnown,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = environment.loginserviceIdportenAudience,
                jwtIssuerType = JwtIssuerType.SELVBETJENING,
                wellKnown = wellKnownSelvbetjening,
            ),
            JwtIssuer(
                acceptedAudienceList = listOf(environment.loginserviceClientId),
                jwtIssuerType = JwtIssuerType.VEILEDER,
                wellKnown = wellKnownVeileder,
            ),
            JwtIssuer(
                acceptedAudienceList = listOf(environment.aadAppClient),
                jwtIssuerType = JwtIssuerType.VEILEDER_V2,
                wellKnown = wellKnownVeilederV2,
            ),
        ),
    )
    installStatusPages()

    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            environment.redisHost,
            environment.redisPort,
            Protocol.DEFAULT_TIMEOUT,
            environment.redisSecret
        )
    )
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
    )
    val adressebeskyttelseClient = AdressebeskyttelseClient(
        azureAdV2Client = azureAdV2Client,
        syfopersonClientId = environment.syfopersonClientId,
        cache = cache,
        syfopersonBaseUrl = environment.syfopersonUrl,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdV2Client = azureAdV2Client,
        syfobehandlendeenhetBaseUrl = environment.syfobehandlendeenhetUrl,
        syfobehandlendeenhetClientId = environment.syfobehandlendeenhetClientId,
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        azureAdV2Client = azureAdV2Client,
        cache = cache,
        syfopersonClientId = environment.syfopersonClientId,
        syfopersonBaseUrl = environment.syfopersonUrl,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        syfopersonBaseUrl = environment.syfopersonUrl,
        syfopersonClientId = environment.syfopersonClientId,
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
        kontaktinformasjonClient = kontaktinformasjonClient,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient
    )

    val arbeidstakerVarselService = ArbeidstakerVarselService(
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        dialogmoteArbeidstakerUrl = environment.dialogmoteArbeidstakerUrl,
        serviceuserUsername = environment.serviceuserUsername,
    )

    val narmesteLederVarselService = NarmesteLederVarselService(
        mqSender = mqSender,
    )

    val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
    )

    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = environment.narmestelederUrl,
        narmestelederClientId = environment.narmestelederClientId,
        azureAdV2Client = azureAdV2Client,
    )

    val dialogmoteService = DialogmoteService(
        database = database,
        arbeidstakerVarselService = arbeidstakerVarselService,
        narmesteLederVarselService = narmesteLederVarselService,
        dialogmotedeltakerService = dialogmotedeltakerService,
        behandlendeEnhetClient = behandlendeEnhetClient,
        narmesteLederClient = narmesteLederClient,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        pdfGenClient = pdfGenClient,
    )

    val narmesteLederService = NarmesteLederService(narmesteLederClient)

    routing {
        registerPodApi(applicationState, database)
        registerPrometheusApi()
        authenticate(JwtIssuerType.VEILEDER.name) {
            registerDialogmoteApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteActionsApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
        }
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
            )
            registerNarmestelederBrevApi(
                dialogmoteService,
                dialogmotedeltakerService,
                narmesteLederService,
            )
        }
    }
}
