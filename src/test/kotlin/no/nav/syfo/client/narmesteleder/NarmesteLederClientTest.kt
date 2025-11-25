package no.nav.syfo.client.narmesteleder

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Token
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonStatus
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import no.nav.syfo.infrastructure.client.tokendings.TokenendingsToken
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class NarmesteLederClientTest {

    private val mapper = configuredJacksonMapper()
    private val anyToken = "token"
    private val anyCallId = "callId"
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val azureAdV2ClientMock = mockk<AzureAdV2Client>()
    private val tokendingsClientMock = mockk<TokendingsClient>()
    private val cacheMock = mockk<ValkeyStore>()
    private val client = NarmesteLederClient(
        narmesteLederBaseUrl = externalMockEnvironment.environment.narmestelederUrl,
        narmestelederClientId = externalMockEnvironment.environment.narmestelederClientId,
        azureAdV2Client = azureAdV2ClientMock,
        tokendingsClient = tokendingsClientMock,
        cache = cacheMock,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val cacheKey =
        "${NarmesteLederClient.CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX}${NARMESTELEDER_FNR.value}"
    private val cachedValue = listOf(
        NarmesteLederRelasjonDTO(
            uuid = UUID.randomUUID().toString(),
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_FNR.value,
            virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
            virksomhetsnavn = "",
            narmesteLederPersonIdentNumber = NARMESTELEDER_FNR.value,
            narmesteLederTelefonnummer = "",
            narmesteLederEpost = "",
            aktivFom = LocalDate.now(),
            aktivTom = null,
            timestamp = LocalDateTime.now(),
            narmesteLederNavn = "",
            arbeidsgiverForskutterer = true,
            status = NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name,
        )
    )

    @BeforeEach
    fun beforeEach() {
        coEvery {
            azureAdV2ClientMock.getSystemToken(externalMockEnvironment.environment.narmestelederClientId)
        } returns AzureAdV2Token(
            accessToken = anyToken,
            expires = LocalDateTime.now().plusDays(1)
        )

        coEvery {
            tokendingsClientMock.getOnBehalfOfToken(externalMockEnvironment.environment.narmestelederClientId, anyToken)
        } returns TokenendingsToken(
            accessToken = anyToken,
            expires = LocalDateTime.now().plusDays(1)
        )

        clearMocks(cacheMock)
    }

    @Test
    fun `aktive ledere returns cached value`() {
        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(cacheKey) } returns mapper.writeValueAsString(cachedValue)

        runBlocking {
            assertEquals(
                1,
                client.getAktiveAnsatte(
                    narmesteLederIdent = NARMESTELEDER_FNR,
                    tokenx = anyToken,
                    callId = anyCallId,
                ).size
            )
        }
        verify(exactly = 1) { cacheMock.get(cacheKey) }
        verify(exactly = 0) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
    }

    @Test
    fun `aktive ledere when no cached value`() {
        every { cacheMock.mapper } returns mapper
        every { cacheMock.get(cacheKey) } returns null
        justRun { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>, any()) }

        runBlocking {
            runBlocking {
                assertEquals(
                    2,
                    client.getAktiveAnsatte(
                        narmesteLederIdent = NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId,
                    ).size
                )
            }
        }
        verify(exactly = 1) { cacheMock.get(cacheKey) }
        verify(exactly = 1) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
    }
}
