package no.nav.syfo.client.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.azuread.AzureAdV2Token
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.AktorId
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.COUNT_CALL_PDL_FAIL
import no.nav.syfo.metric.COUNT_CALL_PDL_SUCCESS
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val pdlClientId: String,
    private val pdlUrl: String,
) {
    private val httpClient = httpClientDefault()

    suspend fun navn(
        personIdent: PersonIdentNumber,
    ): String {
        val token = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return person(personIdent, token)?.fullName()
            ?: throw RuntimeException("PDL returned empty navn for given fnr")
    }

    suspend fun isKode6Or7(
        personIdent: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(pdlClientId, token)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return person(personIdent, oboToken, callId)?.isKode6Or7()
            ?: throw RuntimeException("Person not found in PDL for given fnr")
    }

    private suspend fun person(
        personIdent: PersonIdentNumber,
        token: AzureAdV2Token,
        callId: String? = null,
    ): PdlHentPerson? {
        val query = getPdlQuery("/pdl/hentPerson.graphql")

        val request = PdlRequest(query, Variables(personIdent.value))

        val response: HttpResponse = httpClient.post(pdlUrl) {
            body = request
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.receive<PdlPersonResponse>()
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

    suspend fun aktorId(
        callId: String,
        personIdentNumber: PersonIdentNumber,
    ): AktorId {
        val token = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return identer(
            callId = callId,
            ident = personIdentNumber.value,
            token = token,
        )?.aktorId()
            ?: throw RuntimeException("PDL returned empty AktorId for PersonIdentNumber")
    }

    suspend fun identer(
        callId: String,
        ident: String,
        token: AzureAdV2Token,
    ): PdlHentIdenter? {
        val request = PdlHentIdenterRequest(
            query = getPdlQuery("/pdl/hentIdenter.graphql"),
            variables = PdlHentIdenterRequestVariables(
                ident = ident,
                historikk = false,
                grupper = listOf(
                    IdentType.AKTORID.name,
                    IdentType.FOLKEREGISTERIDENT.name
                )
            )
        )

        val response: HttpResponse = httpClient.post(pdlUrl) {
            body = request
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
            header(IDENTER_HEADER, IDENTER_HEADER)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.receive<PdlIdenterResponse>()
                return if (pdlPersonReponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
                    pdlPersonReponse.data
                } else {
                    COUNT_CALL_PDL_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting Identer from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                }
            }
            else -> {
                COUNT_CALL_PDL_IDENTER_FAIL.increment()
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
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
        const val IDENTER_HEADER = "identer"
    }
}
