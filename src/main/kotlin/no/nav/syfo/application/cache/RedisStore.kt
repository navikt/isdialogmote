package no.nav.syfo.application.cache

import redis.clients.jedis.JedisPool

class RedisStore(private val jedisPool: JedisPool) {

    fun get(key: String): String? {
        jedisPool.resource.use { jedis -> return jedis.get(key) }
    }

    fun set(key: String, value: String, expireSeconds: Int) {
        jedisPool.resource.use { jedis ->
            jedis.setex(
                key,
                expireSeconds,
                value,
            )
        }
    }
}
