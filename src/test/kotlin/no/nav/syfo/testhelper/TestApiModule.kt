package no.nav.syfo.testhelper

import io.ktor.application.*
import io.mockk.mockk
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import redis.clients.jedis.*

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    behandlerVarselService: BehandlerVarselService = mockk(),
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    mqSenderMock: MQSenderInterface,
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
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        behandlerVarselService = behandlerVarselService,
        database = externalMockEnvironment.database,
        mqSender = mqSenderMock,
        environment = externalMockEnvironment.environment,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        wellKnownVeilederV2 = externalMockEnvironment.wellKnownVeilederV2,
        cache = cache,
    )
}
