package no.nav.syfo.client.azuread

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import org.slf4j.LoggerFactory

class AzureAdClient(
    private val aadAppClient: String,
    private val aadAppSecret: String,
    private val aadTokenEndpoint: String,
) {
    private val httpClient = httpClientDefault()

    suspend fun getAccessTokenForResource(resourceClientId: String): AadAccessToken? =
        getAccessToken(
            Parameters.build {
                append("client_id", aadAppClient)
                append("client_secret", aadAppSecret)
                append("scope", "api://$resourceClientId/.default")
                append("grant_type", "client_credentials")
            }
        )

    private suspend fun getAccessToken(formParameters: Parameters): AadAccessToken? {
        return try {
            val response: HttpResponse = httpClient.post(aadTokenEndpoint) {
                accept(ContentType.Application.Json)
                body = FormDataContent(formParameters)
            }
            response.receive<AadAccessToken>()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response)
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
    ): AadAccessToken? {
        log.error(
            "Error while requesting AzureAdAccessToken with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
        )
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(AzureAdClient::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessToken(
    val access_token: String,
    val expires_in: Int,
    val token_type: String,
)
