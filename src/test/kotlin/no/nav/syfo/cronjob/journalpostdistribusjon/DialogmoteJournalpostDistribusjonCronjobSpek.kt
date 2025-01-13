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
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.dialogmote.ReferatJournalpostService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteTidStedPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateEndreDialogmoteTidStedDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmoteJournalpostDistribusjonCronjobSpek : Spek({
    describe(DialogmoteJournalpostDistribusjonCronjob::class.java.simpleName) {
        val arbeidstakerVarselServiceMock = mockk<ArbeidstakerVarselService>(relaxed = true)
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        val dialogmotedeltakerVarselJournalpostService =
            DialogmotedeltakerVarselJournalpostService(database = database)
        val referatJournalpostService = ReferatJournalpostService(database = database)

        val journalpostDistribusjonCronjob = DialogmoteJournalpostDistribusjonCronjob(
            dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
            referatJournalpostService = referatJournalpostService,
            arbeidstakerVarselService = arbeidstakerVarselServiceMock,
        )

        beforeEachTest {
            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
        }

        afterEachTest {
            database.dropData()
        }

        val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            UserConstants.VEILEDER_IDENT,
        )

        describe("Arbeidstaker skal ikke varsles digitalt") {
            val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_IKKE_VARSEL)

            it("Distribuerer journalført innkalling, endring og referat") {

                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).uuid

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

                    client.post(urlMoteUUIDPostTidSted) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(newDialogmoteTidSted)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
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
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 2
                    }

                    coVerify(exactly = 1) { arbeidstakerVarselServiceMock.sendVarsel(MotedeltakerVarselType.NYTT_TID_STED, any(), any(), any(), any()) }
                    coVerify(exactly = 1) { arbeidstakerVarselServiceMock.sendVarsel(MotedeltakerVarselType.INNKALT, any(), any(), any(), any()) }

                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    coVerify(exactly = 1) { arbeidstakerVarselServiceMock.sendVarsel(MotedeltakerVarselType.REFERAT, any(), any(), any(), any()) }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldNotBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldNotBeNull()
                            }
                    }
                }
            }
            it("Distribuerer journalført innkalling med respons 410") {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val dialogmoteDTO = client.postAndGetDialogmote(validToken, newDialogmoteDTO, UserConstants.ARBEIDSTAKER_IKKE_VARSEL)
                    val varselUuids = dialogmoteDTO.arbeidstaker.varselList.map { it.uuid }
                    dialogmoteDTO.arbeidstaker.varselList.first().brevBestiltTidspunkt.shouldBeNull()

                    varselUuids.forEach { database.setMotedeltakerArbeidstakerVarselJournalfort(it, UserConstants.JOURNALPOST_ID_MOTTAKER_GONE) }

                    runBlocking {
                        val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        dialogmoteList.first().arbeidstaker.varselList.first().brevBestiltTidspunkt.shouldNotBeNull()
                    }
                }
            }

            it("Ikke distribuer innkalling og referat som ikke er journalført") {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).uuid

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }
            }

            it("Ikke distribuer journalført innkalling og referat hvor brev er bestilt") {

                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).uuid

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_IKKE_VARSEL).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
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
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                }
            }
        }

        describe("Arbeidstaker varsles digitalt") {
            val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

            it("Ikke distribuer journalført innkalling, endring og referat") {

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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
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
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }
            }

            it("Ikke distribuer innkalling og referat som ikke er journalført") {

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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }
            }

            it("Ikke distribuer journalført innkalling og referat hvor brev er bestilt") {

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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK
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
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                }
            }
        }
    }
})
