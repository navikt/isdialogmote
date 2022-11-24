package no.nav.syfo.client.azuread

import io.ktor.server.testing.TestApplicationEngine
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.AZUREAD_TOKEN
import no.nav.syfo.testhelper.UserConstants.JWT_AZP
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AzureAdClientSpek : Spek({

    val mapper = configuredJacksonMapper()
    val anyToken = "anyToken"

    describe(AzureAdClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val cacheMock = mockk<RedisStore>()

            val azureAdClient = AzureAdV2Client(
                aadAppClient = externalMockEnvironment.environment.aadAppClient,
                aadAppSecret = externalMockEnvironment.environment.aadAppSecret,
                aadTokenEndpoint = externalMockEnvironment.environment.aadTokenEndpoint,
                redisStore = cacheMock,
            )
            val systemTokenCacheKey =
                "${AzureAdV2Client.CACHE_AZUREAD_TOKEN_SYSTEM_KEY_PREFIX}${externalMockEnvironment.environment.pdlClientId}"
            val cachedToken = AzureAdV2Token(anyToken, LocalDateTime.now().plusHours(1))
            val cachedTokenString = mapper.writeValueAsString(cachedToken)

            beforeEachTest {
                clearMocks(cacheMock)
            }

            it("azureAdClient returns cached system token") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(systemTokenCacheKey) } returns cachedTokenString

                runBlocking {
                    azureAdClient.getSystemToken(
                        scopeClientId = externalMockEnvironment.environment.pdlClientId,
                    ) shouldBeEqualTo cachedToken
                }
                verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
                verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
            }

            it("azureAdClient returns new token when cached token missing") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(systemTokenCacheKey) } returns null
                justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

                runBlocking {
                    azureAdClient.getSystemToken(
                        scopeClientId = externalMockEnvironment.environment.pdlClientId,
                    )?.accessToken shouldBeEqualTo AZUREAD_TOKEN
                }
                verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
            }
            val expiredToken = AzureAdV2Token(anyToken, LocalDateTime.now())
            val expiredTokenString = mapper.writeValueAsString(expiredToken)

            it("azureAdClient returns new token when cached token is expired") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(systemTokenCacheKey) } returns expiredTokenString
                justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

                runBlocking {
                    azureAdClient.getSystemToken(
                        scopeClientId = externalMockEnvironment.environment.pdlClientId,
                    )?.accessToken shouldBeEqualTo AZUREAD_TOKEN
                }
                verify(exactly = 1) { cacheMock.get(systemTokenCacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
            }
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )

            val scopeClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId
            val oboTokenCacheKey = "$VEILEDER_IDENT-$JWT_AZP-$scopeClientId"

            it("azureAdClient returns cached obo token") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(oboTokenCacheKey) } returns cachedTokenString

                runBlocking {
                    azureAdClient.getOnBehalfOfToken(
                        scopeClientId = scopeClientId,
                        validToken,
                    ) shouldBeEqualTo cachedToken
                }
                verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
                verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
            }

            it("azureAdClient returns new obo token when cached token missing") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(oboTokenCacheKey) } returns null
                justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

                runBlocking {
                    azureAdClient.getOnBehalfOfToken(
                        scopeClientId = scopeClientId,
                        validToken,
                    )?.accessToken shouldBeEqualTo AZUREAD_TOKEN
                }
                verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
            }

            it("azureAdClient returns new obo token when cached token is expired") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(oboTokenCacheKey) } returns expiredTokenString
                justRun { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }

                runBlocking {
                    azureAdClient.getOnBehalfOfToken(
                        scopeClientId = scopeClientId,
                        validToken,
                    )?.accessToken shouldBeEqualTo AZUREAD_TOKEN
                }
                verify(exactly = 1) { cacheMock.get(oboTokenCacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as AzureAdV2Token, any()) }
            }
        }
    }
})
