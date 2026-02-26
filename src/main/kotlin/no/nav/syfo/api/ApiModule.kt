package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.authentication.JwtIssuer
import no.nav.syfo.api.authentication.JwtIssuerType
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.api.authentication.installCallId
import no.nav.syfo.api.authentication.installContentNegotiation
import no.nav.syfo.api.authentication.installJwtAuthentication
import no.nav.syfo.api.authentication.installMetrics
import no.nav.syfo.api.authentication.installStatusPages
import no.nav.syfo.api.endpoints.registerArbeidstakerBrevApi
import no.nav.syfo.api.endpoints.registerDialogmoteActionsApiV2
import no.nav.syfo.api.endpoints.registerDialogmoteApiV2
import no.nav.syfo.api.endpoints.registerDialogmoteEnhetApiV2
import no.nav.syfo.api.endpoints.registerNarmestelederBrevApi
import no.nav.syfo.api.endpoints.registerPodApi
import no.nav.syfo.api.endpoints.registerPrometheusApi
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.DialogmoteService
import no.nav.syfo.application.DialogmoteTilgangService
import no.nav.syfo.application.DialogmotedeltakerService
import no.nav.syfo.application.DialogmoterelasjonService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.application.IMoteRepository
import no.nav.syfo.application.IPdfRepository
import no.nav.syfo.application.ITransactionManager
import no.nav.syfo.application.NarmesteLederAccessService
import no.nav.syfo.application.NarmesteLederVarselService
import no.nav.syfo.application.VarselService
import no.nav.syfo.infrastructure.client.altinn.AltinnClient
import no.nav.syfo.infrastructure.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer

fun Application.apiModule(
    applicationState: ApplicationState,
    esyfovarselProducer: EsyfovarselProducer,
    behandlerVarselService: BehandlerVarselService,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeilederV2: WellKnown,
    altinnSoapClient: ICorrespondenceAgencyExternalBasic,
    dialogmotestatusService: DialogmotestatusService,
    dialogmoterelasjonService: DialogmoterelasjonService,
    dialogmotedeltakerService: DialogmotedeltakerService,
    arbeidstakerVarselService: ArbeidstakerVarselService,
    pdlClient: PdlClient,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    pdfGenClient: PdfGenClient,
    kontaktinformasjonClient: KontaktinformasjonClient,
    narmesteLederClient: NarmesteLederClient,
    dokumentportenClient: DokumentportenClient,
    pdfRepository: IPdfRepository,
    moteRepository: IMoteRepository,
    transactionManager: ITransactionManager,
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

    val dialogmoteTilgangService = DialogmoteTilgangService(
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
    )

    val narmesteLederVarselService = NarmesteLederVarselService(
        esyfovarselProducer = esyfovarselProducer,
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
        dokumentportenClient = dokumentportenClient,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        isAltinnSendingEnabled = environment.altinnSendingEnabled,
        isDokumentportenSendingEnabled = environment.dokumentportenSendingEnabled
    )

    val dialogmoteService = DialogmoteService(
        transactionManager = transactionManager,
        moteRepository = moteRepository,
        dialogmotedeltakerService = dialogmotedeltakerService,
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        behandlendeEnhetClient = behandlendeEnhetClient,
        narmesteLederClient = narmesteLederClient,
        pdfGenClient = pdfGenClient,
        kontaktinformasjonClient = kontaktinformasjonClient,
        varselService = varselService,
        pdlClient = pdlClient,
        pdfRepository = pdfRepository,
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
                dialogmotestatusService = dialogmotestatusService,
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
                pdlClient = pdlClient,
                pdfRepository = pdfRepository
            )
            registerNarmestelederBrevApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
                narmesteLederAccessService = narmesteLederTilgangService,
                pdfRepository = pdfRepository,
            )
        }
    }
}
