package no.nav.syfo.cronjob.journalpostdistribusjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.dialogmote.ReferatJournalpostService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteTidStedPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateEndreDialogmoteTidStedDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmoteJournalpostDistribusjonCronjobSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(DialogmoteJournalpostDistribusjonCronjob::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)
            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()

            val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock,
            )

            val dialogmotedeltakerVarselJournalpostService =
                DialogmotedeltakerVarselJournalpostService(database = database)
            val referatJournalpostService = ReferatJournalpostService(database = database)
            val journalpostdistribusjonClient = JournalpostdistribusjonClient(
                azureAdV2Client = azureAdV2ClientMock,
                dokdistFordelingClientId = externalMockEnvironment.dokdistMock.name,
                dokdistFordelingBaseUrl = externalMockEnvironment.dokdistMock.url
            )

            val journalpostDistribusjonCronjob = DialogmoteJournalpostDistribusjonCronjob(
                dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
                referatJournalpostService = referatJournalpostService,
                journalpostdistribusjonClient = journalpostdistribusjonClient
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
            val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

            describe("Arbeidstaker skal ikke varsles digitalt") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_IKKE_VARSEL)

                it("Distribuerer journalført innkalling, endring og referat") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
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
                    runBlocking {
                        val result = journalpostDistribusjonCronjob.referatJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldNotBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldNotBeNull()
                            }
                    }
                }
                it("Distribuerer journalført innkalling med respons 410") {
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    val varselUuids: List<String>
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        varselUuids = dialogmoteDTO.arbeidstaker.varselList.map { it.uuid }
                        dialogmoteDTO.arbeidstaker.varselList.first().brevBestiltTidspunkt.shouldBeNull()
                    }

                    varselUuids.forEach { database.setMotedeltakerArbeidstakerVarselJournalfort(it, UserConstants.JOURNALPOST_ID_MOTTAKER_GONE) }

                    runBlocking {
                        val result = journalpostDistribusjonCronjob.dialogmoteVarselJournalpostDistribusjon()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidstaker.varselList.first().brevBestiltTidspunkt.shouldNotBeNull()
                    }
                }

                it("Ikke distribuer innkalling og referat som ikke er journalført") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }
                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }

                it("Ikke distribuer journalført innkalling og referat hvor brev er bestilt") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }
                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        referatUuid = dialogmoteDTO.referatList.first().uuid
                        varselUuids =
                            dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                                .map { it.uuid }
                    }

                    varselUuids.forEach {
                        database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123)
                        database.setMotedeltakerArbeidstakerVarselBrevBestilt(it, "123")
                    }
                    database.setReferatJournalfort(referatUuid, 123)
                    database.setReferatBrevBestilt(referatUuid, "123")

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

            describe("Arbeidstaker varsles digitalt") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

                it("Ikke distribuer journalført innkalling, endring og referat") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
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

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }

                it("Ikke distribuer innkalling og referat som ikke er journalført") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }
                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.referatList.first().brevBestiltTidspunkt.shouldBeNull()
                        dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                            .forEach {
                                it.brevBestiltTidspunkt.shouldBeNull()
                            }
                    }
                }

                it("Ikke distribuer journalført innkalling og referat hvor brev er bestilt") {
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        createdDialogmoteUUID = dialogmoteList.first().uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val referatUuid: String
                    val varselUuids: List<String>
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dialogmoteDTO = dialogmoteList.first()
                        referatUuid = dialogmoteDTO.referatList.first().uuid
                        varselUuids =
                            dialogmoteDTO.arbeidstaker.varselList.filter { it.varselType != MotedeltakerVarselType.REFERAT.name }
                                .map { it.uuid }
                    }

                    varselUuids.forEach {
                        database.setMotedeltakerArbeidstakerVarselJournalfort(it, 123)
                        database.setMotedeltakerArbeidstakerVarselBrevBestilt(it, "123")
                    }
                    database.setReferatJournalfort(referatUuid, 123)
                    database.setReferatBrevBestilt(referatUuid, "123")

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
