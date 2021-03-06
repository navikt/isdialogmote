package no.nav.syfo.client.person.adressebeskyttelse

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.azuread.v2.AzureAdV2Token
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.mock.SyfopersonMock
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class AdressebeskyttelseClientSpek : Spek({

    val arbeidstakerIkkeAdressebeskyttet = ARBEIDSTAKER_FNR
    val arbeidstakerAdressebeskyttet = ARBEIDSTAKER_ADRESSEBESKYTTET
    val arbeidstakerIkkeAdressebeskyttetCacheKey = "person-adressebeskyttelse-${arbeidstakerIkkeAdressebeskyttet.value}"
    val arbeidstakerAdressebeskyttetCacheKey = "person-adressebeskyttelse-${arbeidstakerAdressebeskyttet.value}"

    val anyToken = "token"
    val anyCallId = "callId"

    describe(AdressebeskyttelseClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val azureAdV2ClientMock = mockk<AzureAdV2Client>()
            val syfopersonMock = SyfopersonMock()
            val cacheMock = mockk<RedisStore>()
            val client = AdressebeskyttelseClient(azureAdV2ClientMock, "", cacheMock, syfopersonMock.url)

            coEvery {
                azureAdV2ClientMock.getOnBehalfOfToken("", anyToken)
            } returns AzureAdV2Token(
                accessToken = anyToken,
                expires = LocalDateTime.now().plusDays(1)
            )

            beforeGroup {
                syfopersonMock.server.start()
            }

            afterGroup {
                syfopersonMock.server.stop(1L, 10L)
            }

            beforeEachTest {
                clearMocks(cacheMock)
            }

            it("hasAdressebeskyttelse returns true when cached value is true") {
                every { cacheMock.get(arbeidstakerAdressebeskyttetCacheKey) } returns "true"

                runBlocking {
                    client.hasAdressebeskyttelse(
                        personIdentNumber = arbeidstakerAdressebeskyttet,
                        token = anyToken,
                        callId = anyCallId,
                    ) shouldBeEqualTo true
                }
                verify(exactly = 1) { cacheMock.get(arbeidstakerAdressebeskyttetCacheKey) }
                verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
            }

            it("hasAdressebeskyttelse returns false when cached value is false") {
                every { cacheMock.get(arbeidstakerIkkeAdressebeskyttetCacheKey) } returns "false"

                runBlocking {
                    client.hasAdressebeskyttelse(
                        personIdentNumber = arbeidstakerIkkeAdressebeskyttet,
                        token = anyToken,
                        callId = anyCallId,
                    ) shouldBeEqualTo false
                }
                verify(exactly = 1) { cacheMock.get(arbeidstakerIkkeAdressebeskyttetCacheKey) }
                verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
            }

            it("hasAdressebeskyttelse returns false and caches value when no cached value and arbeidstaker ikke adressebeskyttet") {
                every { cacheMock.get(arbeidstakerIkkeAdressebeskyttetCacheKey) } returns null
                justRun { cacheMock.set(any(), any(), any()) }

                runBlocking {
                    client.hasAdressebeskyttelse(
                        personIdentNumber = arbeidstakerIkkeAdressebeskyttet,
                        token = anyToken,
                        callId = anyCallId,
                    ) shouldBeEqualTo false
                }
                verify(exactly = 1) { cacheMock.get(arbeidstakerIkkeAdressebeskyttetCacheKey) }
                verify(exactly = 1) { cacheMock.set(arbeidstakerIkkeAdressebeskyttetCacheKey, "false", 3600) }
            }

            it("hasAdressebeskyttelse returns true and caches value when no cached value and arbeidstaker adressebeskyttet") {
                every { cacheMock.get(arbeidstakerAdressebeskyttetCacheKey) } returns null
                justRun { cacheMock.set(any(), any(), any()) }

                runBlocking {
                    client.hasAdressebeskyttelse(
                        personIdentNumber = arbeidstakerAdressebeskyttet,
                        token = anyToken,
                        callId = anyCallId,
                    ) shouldBeEqualTo true
                }
                verify(exactly = 1) { cacheMock.get(arbeidstakerAdressebeskyttetCacheKey) }
                verify(exactly = 1) { cacheMock.set(arbeidstakerAdressebeskyttetCacheKey, "true", 3600) }
            }
        }
    }
})
