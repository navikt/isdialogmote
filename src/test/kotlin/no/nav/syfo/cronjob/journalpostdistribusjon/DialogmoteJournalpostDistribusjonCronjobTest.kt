package no.nav.syfo.cronjob.journalpostdistribusjon

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.application.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.application.ReferatJournalpostService
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateEndreDialogmoteTidStedDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DialogmoteJournalpostDistribusjonCronjobTest {

    private val arbeidstakerVarselServiceMock = mockk<ArbeidstakerVarselService>(relaxed = true)
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val altinnResponse = ReceiptExternal()

    private val dialogmotedeltakerVarselJournalpostService =
        DialogmotedeltakerVarselJournalpostService(
            database = database,
            moteRepository = externalMockEnvironment.moteRepository,
        )
    private val referatJournalpostService = ReferatJournalpostService(
        database = database,
        moteRepository = externalMockEnvironment.moteRepository,
    )

    private val journalpostDistribusjonCronjob = DialogmoteJournalpostDistribusjonCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        arbeidstakerVarselService = arbeidstakerVarselServiceMock,
    )

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun beforeEach() {
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Arbeidstaker skal ikke varsles digitalt")
    inner class ArbeidstakerSkalIkkeVarslesDigitalt {

        private val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_IKKE_VARSEL)

        @Test
        fun `Distribuerer journalført innkalling, endring og referat`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID =
                    client.postAndGetDialogmote(
                        validToken,
                        newDialogmoteDTO,
                        UserConstants.ARBEIDSTAKER_IKKE_VARSEL
                    ).uuid

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val referatUuid: String
                val varselUuids: List<String>

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    referatUuid = dialogmoteDTO.referatList.first().uuid
                    varselUuids =
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .map { it.uuid }
                }

                varselUuids.forEach { database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123) }
                database.setReferatJournalfort(referatUuid, 123)

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(2, result.updated)
                }

                coVerify(exactly = 1) {
                    arbeidstakerVarselServiceMock.sendVarsel(
                        MotedeltakerVarselType.NYTT_TID_STED,
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }
                coVerify(exactly = 1) {
                    arbeidstakerVarselServiceMock.sendVarsel(
                        MotedeltakerVarselType.INNKALT,
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }

                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(1, result.updated)
                }

                coVerify(exactly = 1) {
                    arbeidstakerVarselServiceMock.sendVarsel(
                        MotedeltakerVarselType.REFERAT,
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    assertNotNull(dialogmoteDTO.referatList.first().brevBestiltTidspunkt)
                    dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                        .forEach {
                            assertNotNull(it.brevBestiltTidspunkt)
                        }
                }
            }
        }

        @Test
        fun `Distribuerer journalført innkalling med respons 410`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val dialogmoteDTO =
                    client.postAndGetDialogmote(validToken, newDialogmoteDTO, UserConstants.ARBEIDSTAKER_IKKE_VARSEL)
                val varselUuids = dialogmoteDTO.arbeidstaker.varselList.map { it.uuid }
                assertNull(dialogmoteDTO.arbeidstaker.varselList.first().brevBestiltTidspunkt)

                varselUuids.forEach {
                    database.setMotedeltakerArbeidstakerVarselJournalfort(
                        it,
                        UserConstants.JOURNALPOST_ID_MOTTAKER_GONE
                    )
                }

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(1, result.updated)
                }

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertNotNull(dialogmoteList.first().arbeidstaker.varselList.first().brevBestiltTidspunkt)
                }
            }
        }

        @Test
        fun `Ikke distribuer innkalling og referat som ikke er journalført`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID =
                    client.postAndGetDialogmote(
                        validToken,
                        newDialogmoteDTO,
                        UserConstants.ARBEIDSTAKER_IKKE_VARSEL
                    ).uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    assertNull(dialogmoteDTO.referatList.first().brevBestiltTidspunkt)
                    dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                        .forEach {
                            assertNull(it.brevBestiltTidspunkt)
                        }
                }
            }
        }

        @Test
        fun `Ikke distribuer journalført innkalling og referat hvor brev er bestilt`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID =
                    client.postAndGetDialogmote(
                        validToken,
                        newDialogmoteDTO,
                        UserConstants.ARBEIDSTAKER_IKKE_VARSEL
                    ).uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val referatUuid: String
                val varselUuids: List<String>
                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    referatUuid = dialogmoteDTO.referatList.first().uuid
                    varselUuids =
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .map { it.uuid }
                }
                varselUuids.forEach {
                    database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123)
                    database.setMotedeltakerArbeidstakerVarselBrevBestilt(it)
                }
                database.setReferatJournalfort(referatUuid, 123)
                database.setReferatBrevBestilt(referatUuid)

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
            }
        }
    }

    @Nested
    @DisplayName("Arbeidstaker varsles digitalt")
    inner class ArbeidstakerVarslesDigitalt {

        private val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

        @Test
        fun `Ikke distribuer journalført innkalling, endring og referat`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val referatUuid: String
                val varselUuids: List<String>
                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    referatUuid = dialogmoteDTO.referatList.first().uuid
                    varselUuids =
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .map { it.uuid }
                }

                varselUuids.forEach { database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123) }
                database.setReferatJournalfort(referatUuid, 123)

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    assertNull(dialogmoteDTO.referatList.first().brevBestiltTidspunkt)
                    dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                        .forEach {
                            assertNull(it.brevBestiltTidspunkt)
                        }
                }
            }
        }

        @Test
        fun `Ikke distribuer innkalling og referat som ikke er journalført`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    assertNull(dialogmoteDTO.referatList.first().brevBestiltTidspunkt)
                    dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                        .forEach {
                            assertNull(it.brevBestiltTidspunkt)
                        }
                }
            }
        }

        @Test
        fun `Ikke distribuer journalført innkalling og referat hvor brev er bestilt`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                val ferdigstillDialogMoteDto = generateNewReferatDTO()

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(ferdigstillDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val referatUuid: String
                val varselUuids: List<String>

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    referatUuid = dialogmoteDTO.referatList.first().uuid
                    varselUuids =
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .map { it.uuid }
                }

                varselUuids.forEach {
                    database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123)
                    database.setMotedeltakerArbeidstakerVarselBrevBestilt(it)
                }
                database.setReferatJournalfort(referatUuid, 123)
                database.setReferatBrevBestilt(referatUuid)

                runBlocking {
                    val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
                runBlocking {
                    val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                    assertEquals(0, result.failed)
                    assertEquals(0, result.updated)
                }
            }
        }
    }
}
