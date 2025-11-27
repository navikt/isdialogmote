package no.nav.syfo.client.person.kontaktinfo

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.person.kontaktinfo.DigitalKontaktinfoBolk
import no.nav.syfo.infrastructure.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.infrastructure.client.person.kontaktinfo.KontaktinformasjonClient.Companion.CACHE_KONTAKTINFORMASJON_KEY_PREFIX
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.mock.digitalKontaktinfoBolkKanVarslesTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KontaktinformasjonClientTest {

    private val personIdent = UserConstants.ARBEIDSTAKER_FNR
    private val digitalKontaktInfo = digitalKontaktinfoBolkKanVarslesTrue(personIdent.value)
    private val digitalKontaktInfoCacheKey = "$CACHE_KONTAKTINFORMASJON_KEY_PREFIX${personIdent.value}"
    private val anyToken = "token"
    private val anyCallId = "callId"

    private val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
    private val cacheMock = mockk<ValkeyStore>(relaxed = true)
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val client = KontaktinformasjonClient(
        azureAdV2Client = azureAdV2ClientMock,
        cache = cacheMock,
        clientId = externalMockEnvironment.environment.krrClientId,
        baseUrl = externalMockEnvironment.environment.krrUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(cacheMock)
    }

    @Test
    fun `returns cached kontaktinformasjon`() {
        every {
            cacheMock.getObject<DigitalKontaktinfoBolk>(
                digitalKontaktInfoCacheKey
            )
        } returns digitalKontaktInfo

        runBlocking {
            assertTrue(client.isDigitalVarselEnabled(personIdent, anyToken, anyCallId))
        }
        verify(exactly = 1) {
            cacheMock.getObject<DigitalKontaktinfoBolk>(
                digitalKontaktInfoCacheKey
            )
        }
        verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
    }

    @Test
    fun `caches kontaktinformasjon`() {
        every {
            cacheMock.getObject<DigitalKontaktinfoBolk>(
                digitalKontaktInfoCacheKey
            )
        } returns null

        runBlocking {
            assertTrue(client.isDigitalVarselEnabled(personIdent, anyToken, anyCallId))
        }

        verify(exactly = 1) {
            cacheMock.getObject<DigitalKontaktinfoBolk>(
                digitalKontaktInfoCacheKey
            )
        }
        verify(exactly = 1) { cacheMock.setObject(digitalKontaktInfoCacheKey, digitalKontaktInfo, 600) }
    }

    @Test
    fun `handles errors from dkif`() {
        every {
            cacheMock.getObject<DigitalKontaktinfoBolk>(
                digitalKontaktInfoCacheKey
            )
        } returns null

        runBlocking {
            assertFalse(client.isDigitalVarselEnabled(UserConstants.ARBEIDSTAKER_DKIF_FEIL, anyToken, anyCallId))
        }
    }
}
