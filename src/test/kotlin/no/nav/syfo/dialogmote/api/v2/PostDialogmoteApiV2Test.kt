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
import no.nav.syfo.api.endpoints.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PostDialogmoteApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val esyfovarselHendelse = generateInkallingHendelse()
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )

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

        clearMocks(behandlerDialogmeldingProducer)
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        clearMocks(altinnMock)
        clearMocks(esyfovarselProducerMock)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
        @Test
        fun `should return OK if request is successful`() {
            val moteTidspunkt = DIALOGMOTE_TIDSPUNKT_FIXTURE
            val newDialogmoteDTO = generateNewDialogmoteDTO(
                personIdent = ARBEIDSTAKER_FNR,
                dato = moteTidspunkt,
            )

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                verify(exactly = 1) {
                    altinnMock.insertCorrespondenceBasicV2(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(ENHET_NR.value, dialogmoteDTO.tildeltEnhet)
                assertEquals(VEILEDER_IDENT, dialogmoteDTO.tildeltVeilederIdent)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(1, dialogmoteDTO.arbeidstaker.varselList.size)

                assertNull(dialogmoteDTO.behandler)

                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerVarselDTO.varselType)
                assertTrue(arbeidstakerVarselDTO.digitalt)
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
            }
        }

        @Test
        fun `should return OK if request is successful optional values missing`() {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(ENHET_NR.value, dialogmoteDTO.tildeltEnhet)
                assertEquals(VEILEDER_IDENT, dialogmoteDTO.tildeltVeilederIdent)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(1, dialogmoteDTO.arbeidstaker.varselList.size)

                assertNull(dialogmoteDTO.behandler)

                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerVarselDTO.varselType)
                assertTrue(arbeidstakerVarselDTO.digitalt)
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

        @Test
        fun `should return OK if request is successful with behandler`() {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(ENHET_NR.value, dialogmoteDTO.tildeltEnhet)
                assertEquals(VEILEDER_IDENT, dialogmoteDTO.tildeltVeilederIdent)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )
                assertNotNull(dialogmoteDTO.behandler)
                assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, dialogmoteDTO.behandler!!.behandlerRef)
                assertEquals(newDialogmoteDTO.behandler.behandlerNavn, dialogmoteDTO.behandler.behandlerNavn)
                assertEquals(
                    newDialogmoteDTO.behandler.behandlerKontor,
                    dialogmoteDTO.behandler.behandlerKontor
                )
                assertEquals(newDialogmoteDTO.behandler.personIdent, dialogmoteDTO.behandler.personIdent)

                val behandlerVarselDTO = dialogmoteDTO.behandler.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, behandlerVarselDTO.varselType)
                assertEquals("Ipsum lorum behandler", behandlerVarselDTO.fritekst)

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                assertEquals("", dialogmoteDTO.videoLink)

                val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                verify(exactly = 1) {
                    behandlerDialogmeldingProducer.sendDialogmelding(
                        capture(
                            kafkaBehandlerDialogmeldingDTOSlot
                        )
                    )
                }
                val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                assertEquals(newDialogmoteDTO.behandler.behandlerRef, kafkaBehandlerDialogmeldingDTO.behandlerRef)
                assertEquals(
                    newDialogmoteDTO.behandler.innkalling.serialize(),
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                )
                assertEquals(
                    DialogmeldingType.DIALOG_FORESPORSEL.name,
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingType
                )
                assertEquals(
                    DialogmeldingKodeverk.DIALOGMOTE.name,
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk
                )
                assertEquals(DialogmeldingKode.INNKALLING.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                assertNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent)
                assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
                assertEquals("SYFO", kafkaBehandlerDialogmeldingDTO.kilde)
            }
        }

        @Test
        fun `should return OK if request is successful does not have a leader for Virksomhet`() {
            val newDialogmoteDTO =
                generateNewDialogmoteDTO(personIdent = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
                verify(exactly = 1) {
                    altinnMock.insertCorrespondenceBasicV2(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }
            }
        }

        @Test
        fun `should return OK if requesting to create Dialogmote for PersonIdent with inactive Oppfolgingstilfelle`() {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE)
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
        }

        @Test
        fun `return OK when creating dialogmote for innbygger without Oppfolgingstilfelle`() {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE)
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
            val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
            assertEquals(1, moteStatusEndretList.size)

            assertEquals(Dialogmote.Status.INNKALT.name, moteStatusEndretList.first().status.name)
            assertEquals(VEILEDER_IDENT, moteStatusEndretList.first().opprettetAv)
            assertEquals(LocalDate.EPOCH, moteStatusEndretList.first().tilfelleStart)
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {
        private val url = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.post(url)

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
        }

        @Test
        fun `should return status Forbidden if denied access to person`() {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_VEILEDER_NO_ACCESS)
            val urlMotePersonIdent = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.post(urlMotePersonIdent) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteDTO)
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
        }

        @Test
        fun `should return Conflict if requesting to create Dialogmote for PersonIdent with an existing unfinished Dialogmote`() {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )

                client.postMote(validToken, newDialogmoteDTO).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    clearMocks(esyfovarselProducerMock)
                }

                val response = client.postMote(validToken, newDialogmoteDTO)
                assertEquals(HttpStatusCode.Conflict, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
        }

        @Test
        fun `should return InternalServerError if requesting to create Dialogmote for PersonIdent no behandlende enhet`() {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_BEHANDLENDE_ENHET)
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.postMote(validToken, newDialogmoteDTO)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducerMock)
            }
        }
    }
}
