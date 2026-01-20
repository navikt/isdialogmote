package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DocumentComponentType
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.getReferat
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.HendelseType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.mock.pdfReferat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class FerdigstillDialogmoteApiV2AllowVarselMedFysiskBrevTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val esyfovarselHendelse = generateInkallingHendelse()
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val pdfRepository = externalMockEnvironment.pdfRepository

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_IKKE_VARSEL)
        private val newReferatDTO = generateNewReferatDTO()

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)
                    assertTrue(dialogmoteDTO.referatList.isEmpty())

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_REFERAT
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                }

                val referatUuid: String
                client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
                    assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                    assertEquals(
                        newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                    )

                    assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)

                    val referat = dialogmoteDTO.referatList.first()
                    referatUuid = referat.uuid
                    assertFalse(referat.digitalt)
                    assertEquals("Dette er en beskrivelse av situasjonen", referat.situasjon)
                    assertEquals("Grønn Bamse", referat.narmesteLederNavn)
                    assertEquals(DocumentComponentType.HEADER_H1, referat.document[0].type)
                    assertEquals(listOf("Tittel referat"), referat.document[0].texts)

                    assertEquals(DocumentComponentType.PARAGRAPH, referat.document[1].type)
                    assertEquals(listOf("Brødtekst"), referat.document[1].texts)

                    assertEquals(DocumentComponentType.PARAGRAPH, referat.document[2].type)
                    assertEquals("Standardtekst", referat.document[2].key)
                    assertEquals(listOf("Dette er en standardtekst"), referat.document[2].texts)
                    assertNull(referat.brevBestiltTidspunkt)

                    assertEquals("Verneombud", referat.andreDeltakere.first().funksjon)
                    assertEquals("Tøff Pyjamas", referat.andreDeltakere.first().navn)

                    val pdf =
                        pdfRepository.getPdf(database.getReferat(UUID.fromString(referat.uuid)).first().pdfId!!).pdf
                    assertArrayEquals(pdfReferat, pdf)

                    val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                    assertEquals(2, moteStatusEndretList.size)

                    moteStatusEndretList.forEach { moteStatusEndret ->
                        assertEquals(VEILEDER_IDENT, moteStatusEndret.opprettetAv)
                        assertEquals(
                            oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start,
                            moteStatusEndret.tilfelleStart
                        )
                    }
                }
                database.setReferatBrevBestilt(referatUuid)

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL)

                assertEquals(HttpStatusCode.OK, response.status)
                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                val referat = dialogmoteDTO.referatList.first()
                assertNotNull(referat.brevBestiltTidspunkt)
                assertEquals(LocalDate.now(), referat.brevBestiltTidspunkt!!.toLocalDate())
            }
        }
    }
}
