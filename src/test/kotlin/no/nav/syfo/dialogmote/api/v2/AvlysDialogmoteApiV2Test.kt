package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteAvlysPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.time.LocalDateTime

class AvlysDialogmoteApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val altinnResponse = ReceiptExternal()

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK
        clearMocks(behandlerDialogmeldingProducer)
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        clearMocks(esyfovarselProducerMock)
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
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }

                val urlMoteUUIDAvlys =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateAvlysningHendelse()
                        )
                    }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.AVLYST.name, dialogmoteDTO.status)

                    assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                    val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                        it.varselType == MotedeltakerVarselType.AVLYST.name
                    }
                    assertNotNull(arbeidstakerVarselDTO)
                    assertTrue(arbeidstakerVarselDTO.digitalt)
                    assertNull(arbeidstakerVarselDTO.lestDato)
                    assertEquals(avlysDialogMoteDto.arbeidstaker.begrunnelse, arbeidstakerVarselDTO.fritekst)

                    assertEquals(
                        newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                    )
                    val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                        it.varselType == MotedeltakerVarselType.AVLYST.name
                    }
                    assertNotNull(arbeidsgiverVarselDTO)
                    assertEquals(avlysDialogMoteDto.arbeidsgiver.begrunnelse, arbeidsgiverVarselDTO.fritekst)

                    assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                    val isTodayBeforeDialogmotetid =
                        LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                    assertTrue(isTodayBeforeDialogmotetid)

                    verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

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

                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to Avlys Dialogmote: already Avlyst"))
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val newReferatDTO = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to Ferdigstille Dialogmote, already Avlyst"))
                }

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val endreTidStedDialogMoteDto = generateEndreDialogmoteTidStedDTO()

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(endreTidStedDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to change tid/sted, already Avlyst"))
                }
            }
        }
    }

    @Nested
    @DisplayName("With behandler")
    inner class WithBehandler {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDAvlys =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateAvlysningHendelse()
                        )
                    }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(Dialogmote.Status.AVLYST.name, dialogmoteDTO.status)

                val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                verify(exactly = 1) {
                    behandlerDialogmeldingProducer.sendDialogmelding(
                        capture(
                            kafkaBehandlerDialogmeldingDTOSlot
                        )
                    )
                }
                val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, kafkaBehandlerDialogmeldingDTO.behandlerRef)
                assertEquals(
                    avlysDialogMoteDto.behandler!!.avlysning.serialize(),
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                )
                assertEquals(DialogmeldingType.DIALOG_NOTAT.name, kafkaBehandlerDialogmeldingDTO.dialogmeldingType)
                assertEquals(
                    DialogmeldingKodeverk.HENVENDELSE.name,
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk
                )
                assertEquals(DialogmeldingKode.AVLYST.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent)
                assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
            }
        }

        @Test
        fun `should throw exception if mote with behandler and avlysning missing behandler`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDAvlys =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                val avlysDialogMoteDto = generateAvlysDialogmoteDTONoBehandler()

                val response = client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("Failed to Avlys Dialogmote: missing behandler"))
            }
        }
    }

    @Nested
    @DisplayName("Møtet tilbake i tid")
    inner class MotetTilbakeITid {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().plusDays(-30)
        )

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) } // INNKALT

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDAvlys =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 0) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateAvlysningHendelse()
                        )
                    }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(Dialogmote.Status.AVLYST.name, dialogmoteDTO.status)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                    it.varselType == MotedeltakerVarselType.AVLYST.name
                }
                assertNotNull(arbeidstakerVarselDTO)
                assertTrue(arbeidstakerVarselDTO.digitalt)
                assertNull(arbeidstakerVarselDTO.lestDato)
                assertEquals(avlysDialogMoteDto.arbeidstaker.begrunnelse, arbeidstakerVarselDTO.fritekst)

                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )
                val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                    it.varselType == MotedeltakerVarselType.AVLYST.name
                }
                assertNotNull(arbeidsgiverVarselDTO)
                assertEquals(avlysDialogMoteDto.arbeidsgiver.begrunnelse, arbeidsgiverVarselDTO.fritekst)

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                val isTodayBeforeDialogmotetid =
                    LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                assertFalse(isTodayBeforeDialogmotetid)

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
        }
    }
}
