package no.nav.syfo.testhelper

import io.ktor.server.application.Application
import io.mockk.mockk
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import redis.clients.jedis.*

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    behandlerVarselService: BehandlerVarselService = mockk(),
    altinnMock: ICorrespondenceAgencyExternalBasic = mockk(),
    esyfovarselProducer: EsyfovarselProducer = mockk(relaxed = true),
) {
    val redisConfig = externalMockEnvironment.environment.redisConfig
    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(redisConfig.host, redisConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(redisConfig.ssl)
                .password(redisConfig.redisPassword)
                .build()        )
    )
    externalMockEnvironment.redisCache = cache
    val tokendingsClient = TokendingsClient(
        tokenxClientId = externalMockEnvironment.environment.tokenxClientId,
        tokenxEndpoint = externalMockEnvironment.environment.tokenxEndpoint,
        tokenxPrivateJWK = externalMockEnvironment.environment.tokenxPrivateJWK,
    )
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = externalMockEnvironment.environment.aadAppClient,
        aadAppSecret = externalMockEnvironment.environment.aadAppSecret,
        aadTokenEndpoint = externalMockEnvironment.environment.aadTokenEndpoint,
        redisStore = cache,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleClientId = externalMockEnvironment.environment.isoppfolgingstilfelleClientId,
        isoppfolgingstilfelleBaseUrl = externalMockEnvironment.environment.isoppfolgingstilfelleUrl,
        cache = cache,
    )
    val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        moteStatusEndretRepository = MoteStatusEndretRepository(externalMockEnvironment.database),
    )
    val arbeidstakerVarselService = ArbeidstakerVarselService(
        esyfovarselProducer = esyfovarselProducer,
    )
    val dialogmotedeltakerService = DialogmotedeltakerService(
        database = externalMockEnvironment.database,
        arbeidstakerVarselService = arbeidstakerVarselService
    )
    val dialogmoterelasjonService = DialogmoterelasjonService(
        database = externalMockEnvironment.database,
        dialogmotedeltakerService = dialogmotedeltakerService
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        esyfovarselProducer = esyfovarselProducer,
        behandlerVarselService = behandlerVarselService,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        wellKnownVeilederV2 = externalMockEnvironment.wellKnownVeilederV2,
        cache = cache,
        altinnSoapClient = altinnMock,
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        dialogmotedeltakerService = dialogmotedeltakerService,
        arbeidstakerVarselService = arbeidstakerVarselService,
    )
}
