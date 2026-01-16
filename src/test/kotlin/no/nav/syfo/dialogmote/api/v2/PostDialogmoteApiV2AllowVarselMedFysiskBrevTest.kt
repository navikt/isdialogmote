package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.domain.dialogmote.DocumentComponentType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.DIALOGMOTE_TIDSPUNKT_FIXTURE
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.time.LocalDate

class PostDialogmoteApiV2AllowVarselMedFysiskBrevTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val esyfovarselHendelse = generateInkallingHendelse()

    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    @Nested
    @DisplayName("Create Dialogmote for PersonIdent payload")
    inner class CreateDialogmoteForPersonIdentPayload {
        private val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )

        @BeforeEach
        fun setup() {
            database.dropData()

            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse

            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        }

        @Test
        fun `should return OK if request is successful even if ikke-varsle`() {
            val moteTidspunkt = DIALOGMOTE_TIDSPUNKT_FIXTURE
            val newDialogmoteDTO = generateNewDialogmoteDTO(
                personIdent = ARBEIDSTAKER_IKKE_VARSEL,
                dato = moteTidspunkt,
            )

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                val varselUuid: String

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(ENHET_NR.value, dialogmoteDTO.tildeltEnhet)
                assertEquals(VEILEDER_IDENT, dialogmoteDTO.tildeltVeilederIdent)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(1, dialogmoteDTO.arbeidstaker.varselList.size)

                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                varselUuid = arbeidstakerVarselDTO.uuid
                assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerVarselDTO.varselType)
                assertFalse(arbeidstakerVarselDTO.digitalt)
                assertNull(arbeidstakerVarselDTO.lestDato)
                assertEquals("Ipsum lorum arbeidstaker", arbeidstakerVarselDTO.fritekst)

                assertEquals(5, arbeidstakerVarselDTO.document.size)
                assertEquals(DocumentComponentType.PARAGRAPH, arbeidstakerVarselDTO.document[0].type)
                assertEquals("Tittel innkalling", arbeidstakerVarselDTO.document[0].title)
                assertTrue(arbeidstakerVarselDTO.document[0].texts.isEmpty())
                assertEquals(DocumentComponentType.PARAGRAPH, arbeidstakerVarselDTO.document[1].type)
                assertEquals("Møtetid:", arbeidstakerVarselDTO.document[1].title)
                assertEquals(listOf("5. mai 2021"), arbeidstakerVarselDTO.document[1].texts)
                assertEquals(DocumentComponentType.PARAGRAPH, arbeidstakerVarselDTO.document[2].type)
                assertEquals(listOf("Brødtekst"), arbeidstakerVarselDTO.document[2].texts)
                assertEquals(DocumentComponentType.LINK, arbeidstakerVarselDTO.document[3].type)
                assertEquals(listOf("https://nav.no/"), arbeidstakerVarselDTO.document[3].texts)
                assertEquals(DocumentComponentType.PARAGRAPH, arbeidstakerVarselDTO.document[4].type)
                assertEquals(
                    listOf(
                        "Vennlig hilsen",
                        "NAV Staden",
                        "Kari Saksbehandler"
                    ),
                    arbeidstakerVarselDTO.document[4].texts
                )
                assertNull(arbeidstakerVarselDTO.brevBestiltTidspunkt)

                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )
                assertEquals(1, dialogmoteDTO.arbeidsgiver.varselList.size)
                val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidsgiverVarselDTO.varselType)
                assertNull(arbeidsgiverVarselDTO.lestDato)
                assertEquals("Ipsum lorum arbeidsgiver", arbeidsgiverVarselDTO.fritekst)

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                assertEquals("https://meet.google.com/xyz", dialogmoteDTO.videoLink)

                val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                assertEquals(1, moteStatusEndretList.size)

                assertEquals(dialogmoteDTO.status, moteStatusEndretList.first().status.name)
                assertEquals(VEILEDER_IDENT, moteStatusEndretList.first().opprettetAv)
                assertEquals(
                    oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start,
                    moteStatusEndretList.first().tilfelleStart
                )

                database.setMotedeltakerArbeidstakerVarselBrevBestilt(varselUuid)

                client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerVarselDTOBrevBestilt =
                        body<List<DialogmoteDTO>>().first().arbeidstaker.varselList.first()
                    assertNotNull(arbeidstakerVarselDTOBrevBestilt.brevBestiltTidspunkt)
                    assertEquals(
                        LocalDate.now(),
                        arbeidstakerVarselDTOBrevBestilt.brevBestiltTidspunkt!!.toLocalDate()
                    )
                }
            }
        }

        @Test
        fun `should return OK if request is successful optional values missing`() {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_IKKE_VARSEL)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val dialogmoteDTO =
                    client.postAndGetDialogmote(validToken, newDialogmoteDTO, ARBEIDSTAKER_IKKE_VARSEL)

                assertEquals(ENHET_NR.value, dialogmoteDTO.tildeltEnhet)
                assertEquals(VEILEDER_IDENT, dialogmoteDTO.tildeltVeilederIdent)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(1, dialogmoteDTO.arbeidstaker.varselList.size)

                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerVarselDTO.varselType)
                assertFalse(arbeidstakerVarselDTO.digitalt)
                assertNull(arbeidstakerVarselDTO.lestDato)
                assertEquals("", arbeidstakerVarselDTO.fritekst)

                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                assertEquals("", dialogmoteDTO.videoLink)
            }
        }
    }
}
