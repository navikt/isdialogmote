package no.nav.syfo.testhelper

import no.nav.syfo.application.Environment
import redis.embedded.RedisServer

fun testRedis(environment: Environment): RedisServer = RedisServer.builder()
    .port(environment.redisConfig.port)
    .build()
