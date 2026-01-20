package no.nav.syfo.testhelper

import io.ktor.server.netty.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import no.nav.syfo.infrastructure.database.repository.MoteRepository
import no.nav.syfo.infrastructure.database.repository.PdfRepository
import no.nav.syfo.testhelper.mock.*
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    var environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    private val redisConfig = environment.valkeyConfig
    val redisCache = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(redisConfig.host, redisConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(redisConfig.ssl)
                .password(redisConfig.valkeyPassword)
                .build()
        )
    )

    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenxClientId,
        tokenxEndpoint = environment.tokenxEndpoint,
        tokenxPrivateJWK = environment.tokenxPrivateJWK,
        httpClient = mockHttpClient,
    )
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        valkeyStore = redisCache,
        httpClient = mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleClientId = environment.isoppfolgingstilfelleClientId,
        isoppfolgingstilfelleBaseUrl = environment.isoppfolgingstilfelleUrl,
        cache = redisCache,
        httpClient = mockHttpClient,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
        httpClient = mockHttpClient,
        valkeyStore = redisCache,
    )

    val moteRepository = MoteRepository(database)
    val pdfRepository = PdfRepository(database)

    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
    val wellKnownVeilederV2 = wellKnownVeilederV2Mock()

    companion object {
        private val singletonInstance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment()
        }

        fun getInstance(): ExternalMockEnvironment {
            return singletonInstance
        }
    }
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}
