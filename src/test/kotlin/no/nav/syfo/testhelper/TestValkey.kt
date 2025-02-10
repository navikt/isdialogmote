package no.nav.syfo.testhelper

import no.nav.syfo.application.Environment
import redis.embedded.RedisServer

fun testValkey(environment: Environment): RedisServer = RedisServer.builder()
    .port(environment.valkeyConfig.port)
    .setting("requirepass " + environment.valkeyConfig.valkeyPassword)
    .build()
