package no.nav.syfo.client.dokarkiv

import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient.Companion.CACHE_KONTAKTINFORMASJON_KEY_PREFIX
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import no.nav.syfo.testhelper.mock.DokarkivMock
import no.nav.syfo.testhelper.mock.digitalKontaktinfoBolkKanVarslesTrue
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class DokarkivClientSpek : Spek({

    val personIdent = UserConstants.ARBEIDSTAKER_FNR
    val digitalKontaktInfo = digitalKontaktinfoBolkKanVarslesTrue(personIdent.value)
    val digitalKontaktInfoCacheKey = "$CACHE_KONTAKTINFORMASJON_KEY_PREFIX${personIdent.value}"

    val anyToken = "token"
    val anyCallId = "callId"

    describe("DokarkivClient") {

        with(TestApplicationEngine()) {
            start()

            val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
            val dokarkivMock = DokarkivMock()
            val dokarkivClient = DokarkivClient(
                azureAdV2Client = azureAdV2ClientMock,
                dokarkivClientId = "dokarkivClientId",
                dokarkivBaseUrl = dokarkivMock.url,
            )
            val pdf = byteArrayOf(23)

            beforeGroup {
                dokarkivMock.server.start()
            }

            afterGroup {
                dokarkivMock.server.stop(1L, 10L)
            }

            it("journalfører referat") {
                val journalpostRequestReferat = generateJournalpostRequest(
                    tittel = "Referat fra dialogmøte",
                    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AT,
                    pdf = pdf,
                    kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
                    varselId = UUID.randomUUID()
                )

                runBlocking {
                    val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestReferat)

                    response?.journalpostId shouldBeEqualTo 12345678
                }
            }

            it("handles conflict from api when eksternRefeanseId exists by returning null") {
                val journalpostRequestReferat = generateJournalpostRequest(
                    tittel = "Referat fra dialogmøte",
                    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT_AG,
                    pdf = pdf,
                    kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
                    varselId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID,
                )

                runBlocking {
                    val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestReferat)

                    response shouldBeEqualTo null
                }
            }
        }
    }
})
