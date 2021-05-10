package no.nav.syfo.testhelper

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import redis.embedded.RedisServer

fun startExternalMocks(
    applicationMockMap: HashMap<String, NettyApplicationEngine>?,
    embeddedKafkaEnvironment: KafkaEnvironment?,
    embeddedRedisServer: RedisServer?,
) {
    applicationMockMap?.start()
    embeddedKafkaEnvironment?.start()
    embeddedRedisServer?.start()
}

fun stopExternalMocks(
    applicationMockMap: HashMap<String, NettyApplicationEngine>?,
    database: TestDatabase?,
    embeddedKafkaEnvironment: KafkaEnvironment?,
    embeddedRedisServer: RedisServer?,
) {
    applicationMockMap?.stop()
    database?.stop()
    embeddedKafkaEnvironment?.tearDown()
    embeddedRedisServer?.stop()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}

fun HashMap<String, NettyApplicationEngine>.stop(
    gracePeriodMillis: Long = 1L,
    timeoutMillis: Long = 10L,
) {
    this.forEach {
        it.value.stop(gracePeriodMillis, timeoutMillis)
    }
}
