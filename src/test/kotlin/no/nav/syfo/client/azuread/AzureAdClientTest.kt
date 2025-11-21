package no.nav.syfo.client.azuread

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Token
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.AZUREAD_TOKEN
import no.nav.syfo.testhelper.UserConstants.JWT_AZP
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generateJWTNavIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AzureAdClientTest {

    private val mapper = configuredJacksonMapper()
    private val anyToken = "anyToken"
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val cacheMock = mockk<ValkeyStore>()

    private lateinit var azureAdClient: AzureAdV2Client
    private lateinit var systemTokenCacheKey: String
    private lateinit var cachedToken: AzureAdV2Token
    private lateinit var cachedTokenString: String

    @BeforeEach
    fun beforeEach() {
        azureAdClient = AzureAdV2Client(
            aadAppClient = externalMockEnvironment.environment.aadAppClient,
            aadAppSecret = externalMockEnvironment.environment.aadAppSecret,
            aadTokenEndpoint = externalMockEnvironment.environment.aadTokenEndpoint,
            valkeyStore = cacheMock,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        systemTokenCacheKey =
            "${AzureAdV2Client.CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX}${externalMockEnvironment.environment.pdlClientId}"
        cachedToken = AzureAdV2Token(anyToken, LocalDateTime.now().plusHours(1))
        cachedTokenString = mapper.writeValueAsString(cachedToken)

        clearMocks(cacheMock)
    }

    @Test
    fun `azureAdClient returns cached system token`() {
        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(systemTokenCacheKey) } returns cachedTokenString

        runBlocking {
            assertEquals(
                cachedToken, azureAdClient.getSystemToken(
                    scopeClientId = externalMockEnvironment.environment.pdlClientId,
                )
            )
        }
        verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
        verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
    }

    @Test
    fun `azureAdClient returns new token when cached token missing`() {
        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(systemTokenCacheKey) } returns null
        justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

        runBlocking {
            assertEquals(
                AZUREAD_TOKEN, azureAdClient.getSystemToken(
                    scopeClientId = externalMockEnvironment.environment.pdlClientId,
                )?.accessToken
            )
        }
        verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
        verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
    }

    @Test
    fun `azureAdClient returns new token when cached token is expired`() {
        val expiredToken = AzureAdV2Token(anyToken, LocalDateTime.now())
        val expiredTokenString = mapper.writeValueAsString(expiredToken)

        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(systemTokenCacheKey) } returns expiredTokenString
        justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

        runBlocking {
            assertEquals(
                AZUREAD_TOKEN, azureAdClient.getSystemToken(
                    scopeClientId = externalMockEnvironment.environment.pdlClientId,
                )?.accessToken
            )
        }
        verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
        verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
    }

    @Test
    fun `azureAdClient returns cached obo token`() {
        val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )
        val scopeClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId
        val oboTokenCacheKey = "$VEILEDER_IDENT-$JWT_AZP-$scopeClientId"

        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(oboTokenCacheKey) } returns cachedTokenString

        runBlocking {
            assertEquals(
                cachedToken, azureAdClient.getOnBehalfOfToken(
                    scopeClientId = scopeClientId,
                    validToken,
                )
            )
        }
        verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
        verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
    }

    @Test
    fun `azureAdClient returns new obo token when cached token missing`() {
        val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )
        val scopeClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId
        val oboTokenCacheKey = "$VEILEDER_IDENT-$JWT_AZP-$scopeClientId"

        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(oboTokenCacheKey) } returns null
        justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

        runBlocking {
            assertEquals(
                AZUREAD_TOKEN, azureAdClient.getOnBehalfOfToken(
                    scopeClientId = scopeClientId,
                    validToken,
                )?.accessToken
            )
        }
        verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
        verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
    }

    @Test
    fun `azureAdClient returns new obo token when cached token is expired`() {
        val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )
        val scopeClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId
        val oboTokenCacheKey = "$VEILEDER_IDENT-$JWT_AZP-$scopeClientId"
        val expiredToken = AzureAdV2Token(anyToken, LocalDateTime.now())
        val expiredTokenString = mapper.writeValueAsString(expiredToken)

        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(oboTokenCacheKey) } returns expiredTokenString
        justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

        runBlocking {
            assertEquals(
                AZUREAD_TOKEN, azureAdClient.getOnBehalfOfToken(
                    scopeClientId = scopeClientId,
                    validToken,
                )?.accessToken
            )
        }
        verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
        verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
    }
}
