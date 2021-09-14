package no.nav.syfo.client.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
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
        return person(personIdent)?.fullName()
            ?: throw RuntimeException("PDL returned empty navn for given fnr")
    }

    suspend fun person(
        personIdent: PersonIdentNumber,
    ): PdlHentPerson? {
        val token = azureAdV2Client.getSystemToken(pdlClientId)?.accessToken
            ?: throw RuntimeException("Failed to send request to PDL: No accessToken was found")

        val query = this::class.java.getResource("/pdl/hentPerson.graphql")
            .readText()
            .replace("[\n\r]", "")

        val request = PdlRequest(query, Variables(personIdent.value))

        val response: HttpResponse = httpClient.post(pdlUrl) {
            body = request
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
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

    companion object {
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}
