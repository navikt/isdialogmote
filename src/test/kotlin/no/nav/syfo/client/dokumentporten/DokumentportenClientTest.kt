package no.nav.syfo.client.dokumentporten

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenClient
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenDocumentRequestDTO
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DokumentportenClientTest {
    fun dokumentportenDocument(orgNumber: String) = DokumentportenDocumentRequestDTO(
        documentId = UUID.randomUUID(),
        type = DokumentportenDocumentRequestDTO.DocumentType.DIALOGMOTE,
        content = byteArrayOf(23, 45, 67, 89),
        contentType = DokumentportenDocumentRequestDTO.ContentType.PDF,
        orgNumber = orgNumber,
        title = "Test dialogmøte",
        summary = "Dialogmøte opprettet den 01.01.2024",
        fnr = UserConstants.ARBEIDSTAKER_FNR.value,
        fullName = "Test Person"
    )

    val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
    val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    val dokumentportenClient = DokumentportenClient(
        azureAdV2Client = azureAdV2ClientMock,
        baseUrl = externalMockEnvironment.environment.dokumentportenUrl,
        scopeClientId = externalMockEnvironment.environment.dokumentportenClientId,
        client = externalMockEnvironment.mockHttpClient,
    )

    @Test
    fun `sends document successfully`() {
        runTest {
            dokumentportenClient.sendDocument(
                document = dokumentportenDocument(UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                token = "token",
                callId = "callId"
            )
        }
    }

    @Test
    fun `throws DokumentportenClientException when API returns error`() = runTest {
        val exception = assertThrows<DokumentportenClient.DokumentportenClientException> {
            dokumentportenClient.sendDocument(
                document = dokumentportenDocument(UserConstants.VIRKSOMHETSNUMMER_DOKUMENTPORTEN_FAILS.value),
                token = "token",
                callId = "callId"
            )
        }
        assertContains(
            exception.message ?: "",
            "500 Internal Server Error"
        )
    }

    @Test
    fun `Serializes correctly to json`() {
        val json =
            jacksonObjectMapper()
                .writeValueAsString(
                    dokumentportenDocument(UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value)
                )

        assertTrue(json.contains("\"contentType\":\"application/pdf\""))
    }
}
