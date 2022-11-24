package no.nav.syfo.client.narmesteleder

import io.ktor.server.testing.TestApplicationEngine
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.azuread.AzureAdV2Token
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.tokendings.TokenendingsToken
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class NarmesteLederClientSpek : Spek({

    val mapper = configuredJacksonMapper()
    val anyToken = "token"
    val anyCallId = "callId"

    describe(NarmesteLederClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val azureAdV2ClientMock = mockk<AzureAdV2Client>()
            val tokendingsClientMock = mockk<TokendingsClient>()
            val cacheMock = mockk<RedisStore>()
            val client = NarmesteLederClient(
                narmesteLederBaseUrl = externalMockEnvironment.environment.narmestelederUrl,
                narmestelederClientId = externalMockEnvironment.environment.narmestelederClientId,
                azureAdV2Client = azureAdV2ClientMock,
                tokendingsClient = tokendingsClientMock,
                cache = cacheMock,
            )
            val cacheKey =
                "${NarmesteLederClient.CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX}${NARMESTELEDER_FNR.value}"
            val cachedValue = listOf(
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

            beforeEachTest {
                clearMocks(cacheMock)
            }

            it("aktive ledere returns cached value") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(cacheKey) } returns mapper.writeValueAsString(cachedValue)

                runBlocking {
                    client.getAktiveAnsatte(
                        narmesteLederIdent = NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId,
                    ).size shouldBeEqualTo 1
                }
                verify(exactly = 1) { cacheMock.get(cacheKey) }
                verify(exactly = 0) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
            }

            it("aktive ledere when no cached value") {
                every { cacheMock.mapper } returns mapper
                every { cacheMock.get(cacheKey) } returns null
                justRun { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>, any()) }

                runBlocking {
                    runBlocking {
                        client.getAktiveAnsatte(
                            narmesteLederIdent = NARMESTELEDER_FNR,
                            tokenx = anyToken,
                            callId = anyCallId,
                        ).size shouldBeEqualTo 2
                    }
                }
                verify(exactly = 1) { cacheMock.get(cacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
            }
        }
    }
})
