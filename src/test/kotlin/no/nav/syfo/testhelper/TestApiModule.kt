package no.nav.syfo.testhelper

import io.ktor.server.application.Application
import io.mockk.mockk
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    behandlerVarselService: BehandlerVarselService = mockk(),
    altinnMock: ICorrespondenceAgencyExternalBasic = mockk(),
    esyfovarselProducer: EsyfovarselProducer = mockk(relaxed = true),
) {
    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            externalMockEnvironment.environment.redisHost,
            externalMockEnvironment.environment.redisPort,
            Protocol.DEFAULT_TIMEOUT,
            externalMockEnvironment.environment.redisSecret
        )
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        behandlerVarselService = behandlerVarselService,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        wellKnownVeilederV2 = externalMockEnvironment.wellKnownVeilederV2,
        cache = cache,
        altinnSoapClient = altinnMock,
        esyfovarselProducer = esyfovarselProducer,
    )
}
