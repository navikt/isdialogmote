package no.nav.syfo.client.arkivporten

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenClient
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenClientException
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenDocument
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

class ArkivportenClientTest {
    fun arkivportenDocument(orgNumber: String) = ArkivportenDocument(
        documentId = UUID.randomUUID(),
        type = ArkivportenDocument.DocumentType.DIALOGMOTE,
        content = byteArrayOf(23, 45, 67, 89),
        contentType = ArkivportenDocument.ContentType.PDF,
        orgNumber = orgNumber,
        title = "Test dialogmøte",
        summary = "Dialogmøte opprettet den 01.01.2024",
        fnr = UserConstants.ARBEIDSTAKER_FNR.value,
        fullName = "Test Person"
    )

    val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    val arkivportenClient = ArkivportenClient(
        azureAdV2Client = azureAdV2ClientMock,
        baseUrl = externalMockEnvironment.environment.arkivportenUrl,
        clientId = externalMockEnvironment.environment.arkivportenClientId,
        client = externalMockEnvironment.mockHttpClient,
    )

    @Test
    fun `sends document successfully`() {
        runBlocking {
            arkivportenClient.sendDocument(arkivportenDocument(UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value))
        }
    }

    @Test
    fun `throws ArkivportenClientException when API returns error`() {
        var exception: ArkivportenClientException? = null
        runBlocking {
            try {
                arkivportenClient.sendDocument(arkivportenDocument(UserConstants.VIRKSOMHETSNUMMER_ARKIVPORTEN_FAILS.value))
            } catch (e: ArkivportenClientException) {
                exception = e
            }
        }

        assertNotNull(exception)
        assertEquals("Error sending document to Arkivporten: Received status code 500 Internal Server Error", exception?.message)
    }

    @Test
    fun `Serializes correctly to json`() {
        val json =
            jacksonObjectMapper().writeValueAsString(arkivportenDocument(UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value))

        assertTrue(json.contains("\"contentType\":\"application/pdf\""))
    }
}
