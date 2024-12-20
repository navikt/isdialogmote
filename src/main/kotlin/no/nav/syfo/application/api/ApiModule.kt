package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.registerArbeidstakerBrevApi
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.brev.narmesteleder.registerNarmestelederBrevApi
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteActionsApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteApiV2
import no.nav.syfo.dialogmote.api.v2.registerDialogmoteEnhetApiV2
import no.nav.syfo.dialogmote.database.repository.MoteRepository
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService

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

    val dialogmoteService = DialogmoteService(
        database = database,
        moteRepository = MoteRepository(database),
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
