package no.nav.syfo.client.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.azuread.AzureAdV2Token
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.metric.COUNT_CALL_PDL_FAIL
import no.nav.syfo.metric.COUNT_CALL_PDL_SUCCESS
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val pdlClientId: String,
    private val pdlUrl: String,
    private val redisStore: RedisStore,
) {
    private val httpClient = httpClientDefault()

    suspend fun navn(
        personIdent: PersonIdent,
    ): String {
        val cacheKey = "$NAVN_CACHE_KEY_PREFIX${personIdent.value}"
        val cachedNavn: String? = redisStore.get(key = cacheKey)
        return if (cachedNavn != null) {
            cachedNavn
        } else {
            val token = azureAdV2Client.getSystemToken(pdlClientId)
                ?: throw RuntimeException("Failed to send request to PDL: No token was found")
            val navn = person(personIdent, token)?.fullName()
                ?: throw RuntimeException("PDL returned empty navn for given fnr")
            redisStore.set(cacheKey, navn, CACHE_EXPIRE_SECONDS)
            navn
        }
    }

    suspend fun isKode6Or7(
        personIdent: PersonIdent,
        callId: String,
    ): Boolean {
        val systemToken = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return person(personIdent, systemToken, callId)?.isKode6Or7()
            ?: throw RuntimeException("Person not found in PDL for given fnr")
    }

    suspend fun hentFolkeregisterIdenter(
        personIdent: PersonIdent,
        callId: String,
    ): Set<PersonIdent> {
        val cacheKey = "$FOLKEREG_IDENTER_CACHE_KEY_PREFIX${personIdent.value}"
        val cachedIdenter: Set<PersonIdent>? = redisStore.getSetObject(key = cacheKey)
        return if (cachedIdenter != null) {
            cachedIdenter
        } else {
            mutableSetOf(personIdent).also {
                it.addAll(
                    hentIdenter(
                        nyPersonIdent = personIdent.value,
                        callId = callId,
                    )?.identer?.filter { pdlIdent ->
                        pdlIdent.gruppe == IdentGruppe.FOLKEREGISTERIDENT
                    }?.map { pdlIdent ->
                        PersonIdent(pdlIdent.ident)
                    } ?: emptySet()
                )
            }.toSet().also {
                redisStore.setObject(cacheKey, it, CACHE_EXPIRE_SECONDS)
            }
        }
    }

    suspend fun hentIdenter(
        nyPersonIdent: String,
        callId: String? = null,
    ): PdlIdenter? {
        val systemToken = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")

        val query = getPdlQuery("/pdl/hentIdenter.graphql")
        val request = PdlRequest(query, Variables(ident = nyPersonIdent))

        val response: HttpResponse = httpClient.post(pdlUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(systemToken.accessToken))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val pdlIdenterReponse = response.body<PdlIdentResponse>()
                if (!pdlIdenterReponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_FAIL.increment()
                    pdlIdenterReponse.errors.forEach {
                        logger.error("Error while requesting ident from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_SUCCESS.increment()
                    pdlIdenterReponse.data?.hentIdenter
                }
            }
            else -> {
                COUNT_CALL_PDL_FAIL.increment()
                val message = "Request with url: $pdlUrl failed with reponse code ${response.status.value}"
                logger.error(message)
                throw RuntimeException(message)
            }
        }
    }

    private suspend fun person(
        personIdent: PersonIdent,
        token: AzureAdV2Token,
        callId: String? = null,
    ): PdlHentPerson? {
        val query = getPdlQuery("/pdl/hentPerson.graphql")

        val request = PdlRequest(query, Variables(personIdent.value))

        val response: HttpResponse = httpClient.post(pdlUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<PdlPersonResponse>()
                return if (pdlPersonReponse.errors != null && pdlPersonReponse.errors.isNotEmpty()) {
                    COUNT_CALL_PDL_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_SUCCESS.increment()
                    pdlPersonReponse.data
                }
            }
            else -> {
                COUNT_CALL_PDL_FAIL.increment()
                logger.error("Request with url: $pdlUrl failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    private fun getPdlQuery(queryFilePath: String): String {
        return this::class.java.getResource(queryFilePath)
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        private val NAVN_CACHE_KEY_PREFIX = "pdl-navn"
        private val FOLKEREG_IDENTER_CACHE_KEY_PREFIX = "pdl-folkereg-identer"
        private val CACHE_EXPIRE_SECONDS = 24L * 3600
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}
