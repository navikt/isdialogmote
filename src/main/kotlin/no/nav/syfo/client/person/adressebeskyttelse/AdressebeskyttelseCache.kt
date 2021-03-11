package no.nav.syfo.client.person.adressebeskyttelse

import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.params.SetParams

class AdressebeskyttelseCache(
    private val jedis: Jedis,
) {

    fun hasAdressebeskyttelse(personIdentNumber: PersonIdentNumber): Boolean? {
        jedis.use { jedisClient ->
            jedisClient.get(cacheKey(personIdentNumber))?.let { value ->
                log.info("JTRACE: return cached value $value")
                value.toBoolean()
            }
        }
        return null
    }

    fun setAdressebeskyttelse(personIdentNumber: PersonIdentNumber, beskyttet: Boolean) {
        jedis.use { jedisClient ->
            jedisClient.set(
                cacheKey(personIdentNumber),
                beskyttet.toString(),
                SetParams().ex(CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS)
            )
        }
        log.info("JTRACE: set cached value $beskyttet")
    }

    private fun cacheKey(personIdentNumber: PersonIdentNumber): String {
        return "$CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX${personIdentNumber.value}"
    }

    companion object {
        const val CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX = "person-adressebeskyttelse-"
        const val CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS = 3600

        private val log = LoggerFactory.getLogger(AdressebeskyttelseCache::class.java)
    }
}
