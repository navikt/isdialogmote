package no.nav.syfo.infrastructure.client.azuread

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.api.authentication.getConsumerClientId
import no.nav.syfo.api.authentication.getNAVIdentFromToken
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.httpClientProxy
import org.slf4j.LoggerFactory

class AzureAdV2Client(
    private val aadAppClient: String,
    private val aadAppSecret: String,
    private val aadTokenEndpoint: String,
    private val valkeyStore: ValkeyStore,
    private val httpClient: HttpClient = httpClientProxy(),
) {

    suspend fun getOnBehalfOfToken(
        scopeClientId: String,
        token: String,
    ): AzureAdV2Token? {
        val azp = getConsumerClientId(token)
        val veilederIdent = getNAVIdentFromToken(token)

        val cacheKey = "$veilederIdent-$azp-$scopeClientId"
        val cachedToken: AzureAdV2Token? = valkeyStore.getObject(key = cacheKey)
        return if (cachedToken?.isExpired() == false) {
            COUNT_CALL_AZUREAD_TOKEN_OBO_CACHE_HIT.increment()
            cachedToken
        } else {
            val azureAdTokenResponse = getAccessToken(
                Parameters.build {
                    append("client_id", aadAppClient)
                    append("client_secret", aadAppSecret)
                    append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    append("assertion", token)
                    append("scope", "api://$scopeClientId/.default")
                    append("requested_token_use", "on_behalf_of")
                }
            )
            azureAdTokenResponse?.let { tokenResponse ->
                val azureAdToken = tokenResponse.toAzureAdV2Token()
                COUNT_CALL_AZUREAD_TOKEN_OBO_CACHE_MISS.increment()
                valkeyStore.setObject(
                    expireSeconds = 3600,
                    key = cacheKey,
                    value = azureAdToken,
                )
                azureAdToken
            }
        }
    }

    suspend fun getSystemToken(scopeClientId: String): AzureAdV2Token? {
        val cacheKey = "${CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX}$scopeClientId"
        val cachedToken = valkeyStore.getObject<AzureAdV2Token>(key = cacheKey)
        return if (cachedToken?.isExpired() == false) {
            COUNT_CALL_AZUREAD_TOKEN_SYSTEM_CACHE_HIT.increment()
            cachedToken
        } else {
            val azureAdTokenResponse = getAccessToken(
                Parameters.build {
                    append("client_id", aadAppClient)
                    append("client_secret", aadAppSecret)
                    append("grant_type", "client_credentials")
                    append("scope", "api://$scopeClientId/.default")
                }
            )
            azureAdTokenResponse?.let { token ->
                val azureAdToken = token.toAzureAdV2Token()
                COUNT_CALL_AZUREAD_TOKEN_SYSTEM_CACHE_MISS.increment()
                valkeyStore.setObject(
                    expireSeconds = 3600,
                    key = cacheKey,
                    value = azureAdToken,
                )
                azureAdToken
            }
        }
    }

    private suspend fun getAccessToken(formParameters: Parameters): AzureAdV2TokenResponse? {
        return try {
            val response: HttpResponse = httpClient.post(aadTokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(formParameters))
            }
            response.body<AzureAdV2TokenResponse>()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e)
        }
    }

    private fun handleUnexpectedResponseException(
        responseException: ResponseException,
    ): AzureAdV2TokenResponse? {
        log.error(
            "Error while requesting AzureAdAccessToken with statusCode=${responseException.response.status.value}",
            responseException
        )
        return null
    }

    companion object {
        const val CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX = "azuread-token-system-"
        private val log = LoggerFactory.getLogger(AzureAdV2Client::class.java)
    }
}
