package no.nav.syfo.testhelper

import io.ktor.application.*
import io.mockk.mockk
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import redis.clients.jedis.*

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    behandlerDialogmeldingProducer: BehandlerDialogmeldingProducer = mockk(),
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
    val behandlerVarselService = BehandlerVarselService(
        database = externalMockEnvironment.database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
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
