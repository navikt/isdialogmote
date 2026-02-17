package no.nav.syfo.testhelper

import io.ktor.server.application.*
import io.mockk.mockk
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.*
import no.nav.syfo.infrastructure.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    behandlerVarselService: BehandlerVarselService = mockk(),
    altinnMock: ICorrespondenceAgencyExternalBasic = mockk(),
    esyfovarselProducer: EsyfovarselProducer = mockk(relaxed = true),
) {
    val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = externalMockEnvironment.oppfolgingstilfelleClient,
        moteStatusEndretRepository = MoteStatusEndretRepository(externalMockEnvironment.database),
    )
    val arbeidstakerVarselService = ArbeidstakerVarselService(
        esyfovarselProducer = esyfovarselProducer,
    )
    val dialogmotedeltakerService = DialogmotedeltakerService(
        database = externalMockEnvironment.database,
        arbeidstakerVarselService = arbeidstakerVarselService,
        moteRepository = externalMockEnvironment.moteRepository,
    )
    val dialogmoterelasjonService = DialogmoterelasjonService(
        database = externalMockEnvironment.database,
        dialogmotedeltakerService = dialogmotedeltakerService,
        moteRepository = externalMockEnvironment.moteRepository,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        tilgangskontrollClientId = externalMockEnvironment.environment.istilgangskontrollClientId,
        tilgangskontrollBaseUrl = externalMockEnvironment.environment.istilgangskontrollUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        syfobehandlendeenhetClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId,
        syfobehandlendeenhetBaseUrl = externalMockEnvironment.environment.syfobehandlendeenhetUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = externalMockEnvironment.environment.ispdfgenUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        clientId = externalMockEnvironment.environment.krrClientId,
        baseUrl = externalMockEnvironment.environment.krrUrl,
        cache = externalMockEnvironment.redisCache,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = externalMockEnvironment.environment.narmestelederUrl,
        narmestelederClientId = externalMockEnvironment.environment.narmestelederClientId,
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        tokendingsClient = externalMockEnvironment.tokendingsClient,
        cache = externalMockEnvironment.redisCache,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val dokumentportenClient = no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenClient(
        baseUrl = externalMockEnvironment.environment.dokumentportenUrl,
        azureAdV2Client = externalMockEnvironment.azureAdV2Client,
        scopeClientId = externalMockEnvironment.environment.dokumentportenClientId,
        client = externalMockEnvironment.mockHttpClient,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        esyfovarselProducer = esyfovarselProducer,
        behandlerVarselService = behandlerVarselService,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        wellKnownVeilederV2 = externalMockEnvironment.wellKnownVeilederV2,
        altinnSoapClient = altinnMock,
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        dialogmotedeltakerService = dialogmotedeltakerService,
        arbeidstakerVarselService = arbeidstakerVarselService,
        pdlClient = externalMockEnvironment.pdlClient,
        oppfolgingstilfelleClient = externalMockEnvironment.oppfolgingstilfelleClient,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        behandlendeEnhetClient = behandlendeEnhetClient,
        pdfGenClient = pdfGenClient,
        kontaktinformasjonClient = kontaktinformasjonClient,
        narmesteLederClient = narmesteLederClient,
        dokumentportenClient = dokumentportenClient,
        pdfRepository = externalMockEnvironment.pdfRepository,
        moteRepository = externalMockEnvironment.moteRepository,
    )
}
