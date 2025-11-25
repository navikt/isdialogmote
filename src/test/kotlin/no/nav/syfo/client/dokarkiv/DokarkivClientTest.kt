package no.nav.syfo.client.dokarkiv

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class DokarkivClientTest {

    private val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val dokarkivClient = DokarkivClient(
        azureAdV2Client = azureAdV2ClientMock,
        dokarkivClientId = externalMockEnvironment.environment.dokarkivClientId,
        dokarkivBaseUrl = externalMockEnvironment.environment.dokarkivUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val pdf = byteArrayOf(23)

    @Test
    fun `journalfører referat`() {
        val journalpostRequestReferat = generateJournalpostRequest(
            tittel = "Referat fra dialogmøte",
            brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AT,
            pdf = pdf,
            kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
            varselId = UUID.randomUUID()
        )

        runBlocking {
            val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestReferat)

            assertEquals(UserConstants.JOURNALPOSTID_JOURNALFORING, response?.journalpostId)
        }
    }

    @Test
    fun `handles conflict from api when eksternRefeanseId exists by returning journalpostid`() {
        val journalpostRequestReferat = generateJournalpostRequest(
            tittel = "Referat fra dialogmøte",
            brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AG,
            pdf = pdf,
            kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
            varselId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID,
        )

        runBlocking {
            val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestReferat)

            assertEquals(UserConstants.JOURNALPOSTID_JOURNALFORING, response?.journalpostId)
        }
    }
}
