package no.nav.syfo.application.cache

import no.nav.syfo.client.defaultJacksonMapper
import redis.clients.jedis.JedisPool

class RedisStore(private val jedisPool: JedisPool) {

    val mapper = defaultJacksonMapper()

    inline fun <reified T> getObject(key: String): T? {
        val value = get(key)
        return if (value != null) mapper.readValue(value, T::class.java) else null
    }

    fun get(key: String): String? {
        jedisPool.resource.use { jedis -> return jedis.get(key) }
    }

    fun <T> setObject(key: String, value: T, expireSeconds: Int) {
        set(key, mapper.writeValueAsString(value), expireSeconds)
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
