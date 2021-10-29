package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.registerArbeidstakerBrevApi
import no.nav.syfo.brev.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.brev.narmesteleder.registerNarmestelederBrevApi
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService

fun Application.apiModule(
    applicationState: ApplicationState,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    database: DatabaseInterface,
    mqSender: MQSenderInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeilederV2: WellKnown,
    cache: RedisStore,
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
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
    )
    val adressebeskyttelseClient = AdressebeskyttelseClient(
        pdlClient = pdlClient,
        cache = cache
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdV2Client = azureAdV2Client,
        syfobehandlendeenhetBaseUrl = environment.syfobehandlendeenhetUrl,
        syfobehandlendeenhetClientId = environment.syfobehandlendeenhetClientId,
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        azureAdV2Client = azureAdV2Client,
        cache = cache,
        isproxyClientId = environment.isproxyClientId,
        isproxyBaseUrl = environment.isproxyUrl,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        isproxyBaseUrl = environment.isproxyUrl,
        isproxyClientId = environment.isproxyClientId,
        pdlClient = pdlClient,
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
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        allowVarselMedFysiskBrev = environment.allowVarselMedFysiskBrev,
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
        cache = cache,
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
        kontaktinformasjonClient = kontaktinformasjonClient,
        allowVarselMedFysiskBrev = environment.allowVarselMedFysiskBrev,
    )

    val narmesteLederTilgangService = NarmesteLederAccessService(
        dialogmotedeltakerService = dialogmotedeltakerService,
        narmesteLederClient = narmesteLederClient,
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
            )
            registerNarmestelederBrevApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
                narmesteLederAccessService = narmesteLederTilgangService,
            )
        }
    }
}
