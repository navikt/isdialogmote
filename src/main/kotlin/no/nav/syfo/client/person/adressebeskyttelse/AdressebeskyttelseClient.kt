package no.nav.syfo.client.person.adressebeskyttelse

import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.time.Duration

class AdressebeskyttelseClient(
    private val pdlClient: PdlClient,
    private val cache: RedisStore,
) {

    suspend fun hasAdressebeskyttelse(
        personIdent: PersonIdent,
        callId: String,
    ): Boolean {
        val cacheKey = "$CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX${personIdent.value}"
        val cachedAdressebeskyttelse = cache.get(cacheKey)
        return when (cachedAdressebeskyttelse) {
            null -> {
                val starttime = System.currentTimeMillis()
                try {
                    val hasAdressebeskyttelse = pdlClient.isKode6Or7(
                        callId = callId,
                        personIdent = personIdent,
                    )
                    cache.set(
                        cacheKey,
                        hasAdressebeskyttelse.toString(),
                        CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS
                    )
                    hasAdressebeskyttelse
                } catch (e: ClientRequestException) {
                    handleUnexpectedResponseException(e.response, callId)
                } catch (e: ServerResponseException) {
                    handleUnexpectedResponseException(e.response, callId)
                } catch (e: ClosedReceiveChannelException) {
                    handleClosedReceiveChannelException(e)
                } finally {
                    val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
                    HISTOGRAM_CALL_PERSON_ADRESSEBESKYTTELSE_TIMER.record(duration)
                }
            }
            else -> cachedAdressebeskyttelse.toBoolean()
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ): Boolean {
        log.error(
            "Error while requesting Adressebeskyttelse of person from PDL with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            callIdArgument(callId)
        )
        return true
    }

    private fun handleClosedReceiveChannelException(
        e: ClosedReceiveChannelException
    ): Boolean {
        throw RuntimeException("Caught ClosedReceiveChannelException in hasAdressebeskyttelse", e)
    }

    companion object {
        const val CACHE_ADRESSEBESKYTTELSE_KEY_PREFIX = "person-adressebeskyttelse-"
        const val CACHE_ADRESSEBESKYTTELSE_EXPIRE_SECONDS = 3600L

        private val log = LoggerFactory.getLogger(AdressebeskyttelseClient::class.java)
    }
}
