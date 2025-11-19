package no.nav.syfo.client.arkivporten

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenClient
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenClientException
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenDocument
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ArkivportenClientSpek : Spek({
    describe("ArkivportenClient") {
        val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val arkivportenClient = ArkivportenClient(
            azureAdV2Client = azureAdV2ClientMock,
            baseUrl = externalMockEnvironment.environment.arkivportenUrl,
            arkivportenScope = externalMockEnvironment.environment.arkivportenScope,
            client = externalMockEnvironment.mockHttpClient,
        )
        val pdf = byteArrayOf(23, 45, 67, 89)

        it("sends document successfully") {
            val document = ArkivportenDocument(
                documentId = UUID.randomUUID(),
                type = ArkivportenDocument.DocumentType.DIALOGMOTE,
                content = pdf,
                contentType = ArkivportenDocument.ContentType.PDF,
                orgnumber = UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                dialogTitle = "Test dialogmøte",
                dialogSummary = "Dialogmøte opprettet den 01.01.2024"
            )

            runBlocking {
                arkivportenClient.sendDocument(document)
            }
        }

        it("throws ArkivportenClientException when API returns error") {
            val document = ArkivportenDocument(
                documentId = UUID.randomUUID(),
                type = ArkivportenDocument.DocumentType.DIALOGMOTE,
                content = pdf,
                contentType = ArkivportenDocument.ContentType.PDF,
                orgnumber = UserConstants.VIRKSOMHETSNUMMER_EREG_FAILS.value,
                dialogTitle = "Test dialogmøte",
                dialogSummary = "Dialogmøte opprettet den 01.01.2024"
            )

            var exception: ArkivportenClientException? = null
            runBlocking {
                try {
                    arkivportenClient.sendDocument(document)
                } catch (e: ArkivportenClientException) {
                    exception = e
                }
            }

            exception.shouldNotBeNull()
            exception.message shouldBeEqualTo "Error sending document to Arkivporten: Received status code 500 Internal Server Error"
        }

        it("Serializes correctly to json") {
            val document = ArkivportenDocument(
                documentId = UUID.randomUUID(),
                type = ArkivportenDocument.DocumentType.DIALOGMOTE,
                content = pdf,
                contentType = ArkivportenDocument.ContentType.PDF,
                orgnumber = UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                dialogTitle = "Test dialogmøte",
                dialogSummary = "Dialogmøte opprettet den 01.01.2024"
            )
            val json = jacksonObjectMapper().writeValueAsString(document)
            json.contains("\"contentType\":\"application/pdf\"") shouldBe true
        }
    }
})
