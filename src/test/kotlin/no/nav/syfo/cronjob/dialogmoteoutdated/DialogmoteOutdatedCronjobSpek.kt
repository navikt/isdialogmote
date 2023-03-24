package no.nav.syfo.cronjob.dialogmoteoutdated

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateAvlysDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateEndreDialogmoteTidStedDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmoteOutdatedCronjobSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(DialogmoteOutdatedCronjobSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )

            val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)

            val tokendingsClient = mockk<TokendingsClient>(relaxed = true)

            val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
                azureAdV2Client = azureAdV2Client,
                tokendingsClient = tokendingsClient,
                isoppfolgingstilfelleClientId = externalMockEnvironment.environment.isoppfolgingstilfelleClientId,
                isoppfolgingstilfelleBaseUrl = externalMockEnvironment.environment.isoppfolgingstilfelleUrl,
                cache = mockk<RedisStore>(relaxed = true),
            )
            val dialogmotestatusService = DialogmotestatusService(
                oppfolgingstilfelleClient = oppfolgingstilfelleClient,
            )
            val arbeidstakerVarselService = ArbeidstakerVarselService(
                esyfovarselProducer = esyfovarselProducerMock,
            )
            val dialogmotedeltakerService = DialogmotedeltakerService(
                arbeidstakerVarselService = arbeidstakerVarselService,
                database = database,
            )
            val dialogmoterelasjonService = DialogmoterelasjonService(
                dialogmotedeltakerService = dialogmotedeltakerService,
                database = database,
            )

            val dialogmoteOutdatedCronjob = DialogmoteOutdatedCronjob(
                dialogmotestatusService = dialogmotestatusService,
                dialogmoterelasjonService = dialogmoterelasjonService,
                database = database,
                outdatedDialogmoterCutoff = LocalDate.now().minusDays(30),
            )

            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            afterEachTest {
                database.dropData()
            }

            describe("Cronjob for status paa gamle moter") {
                val validToken = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                it("Setter status paa gammel innkalling til LUKKET") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                        dato = LocalDateTime.now().minusDays(40),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteOutdatedCronjob.dialogmoteOutdatedJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.LUKKET.name
                    }
                }
                it("Setter status paa gammel innkalling med status NYTT_TID_STED til LUKKET") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                        dato = LocalDateTime.now().minusDays(40),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    val createdDialogmoteUUID: String
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        dialogmoteList.size shouldBeEqualTo 1
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newTidStedDTO = generateEndreDialogmoteTidStedDTO(
                        tid = LocalDateTime.now().minusDays(39),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newTidStedDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteOutdatedCronjob.dialogmoteOutdatedJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.LUKKET.name
                    }
                }
                it("Endrer ikke status paa ny innkalling") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                        dato = LocalDateTime.now().minusDays(20),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteOutdatedCronjob.dialogmoteOutdatedJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                    }
                }
                it("Setter ikke status paa gammelt mote med status FERDIGSTILT til LUKKET") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                        dato = LocalDateTime.now().minusDays(40),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    val createdDialogmoteUUID: String
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        dialogmoteList.size shouldBeEqualTo 1
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(generateNewReferatDTO()))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteOutdatedCronjob.dialogmoteOutdatedJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                    }
                }
                it("Setter ikke status paa gammelt mote med status AVLYST til LUKKET") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                        dato = LocalDateTime.now().minusDays(40),
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    val createdDialogmoteUUID: String
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        dialogmoteList.size shouldBeEqualTo 1
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }
                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(generateAvlysDialogmoteDTO()))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteOutdatedCronjob.dialogmoteOutdatedJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.AVLYST.name
                    }
                }
            }
        }
    }
})
