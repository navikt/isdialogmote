package no.nav.syfo.client.person.kontaktinfo

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient.Companion.CACHE_KONTAKTINFORMASJON_KEY_PREFIX
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.mock.digitalKontaktinfoBolkKanVarslesTrue
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class KontaktinformasjonClientSpek : Spek({

    val personIdent = UserConstants.ARBEIDSTAKER_FNR
    val digitalKontaktInfo = digitalKontaktinfoBolkKanVarslesTrue(personIdent.value)
    val digitalKontaktInfoCacheKey = "$CACHE_KONTAKTINFORMASJON_KEY_PREFIX${personIdent.value}"

    val anyToken = "token"
    val anyCallId = "callId"

    describe(KontaktinformasjonClientSpek::class.java.simpleName) {
        val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
        val cacheMock = mockk<RedisStore>(relaxed = true)
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val client = KontaktinformasjonClient(
            azureAdV2Client = azureAdV2ClientMock,
            cache = cacheMock,
            clientId = externalMockEnvironment.environment.krrClientId,
            baseUrl = externalMockEnvironment.environment.krrUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )

        beforeEachTest {
            clearMocks(cacheMock)
        }

        it("returns cached kontaktinformasjon") {
            every {
                cacheMock.getObject<DigitalKontaktinfoBolk>(
                    digitalKontaktInfoCacheKey
                )
            } returns digitalKontaktInfo

            runBlocking {
                client.isDigitalVarselEnabled(personIdent, anyToken, anyCallId) shouldBeEqualTo true
            }
            verify(exactly = 1) {
                cacheMock.getObject<DigitalKontaktinfoBolk>(
                    digitalKontaktInfoCacheKey
                )
            }
            verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
        }

        it("caches kontaktinformasjon") {
            every {
                cacheMock.getObject<DigitalKontaktinfoBolk>(
                    digitalKontaktInfoCacheKey
                )
            } returns null

            runBlocking {
                client.isDigitalVarselEnabled(personIdent, anyToken, anyCallId) shouldBeEqualTo true
            }

            verify(exactly = 1) {
                cacheMock.getObject<DigitalKontaktinfoBolk>(
                    digitalKontaktInfoCacheKey
                )
            }
            verify(exactly = 1) { cacheMock.setObject(digitalKontaktInfoCacheKey, digitalKontaktInfo, 600) }
        }
        it("handles errors from dkif") {
            every {
                cacheMock.getObject<DigitalKontaktinfoBolk>(
                    digitalKontaktInfoCacheKey
                )
            } returns null

            runBlocking {
                client.isDigitalVarselEnabled(UserConstants.ARBEIDSTAKER_DKIF_FEIL, anyToken, anyCallId) shouldBeEqualTo false
            }
        }
    }
})
