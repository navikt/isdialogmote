package no.nav.syfo.cronjob.dialogmoteoutdated

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

class DialogmoteOutdatedCronjobSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(DialogmoteOutdatedCronjobSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val dineSykmeldteVarselProducer = mockk<DineSykmeldteVarselProducer>()
            justRun { dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
                mqSenderMock = mqSenderMock,
                altinnMock = altinnMock,
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
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                dialogmoteArbeidstakerUrl = externalMockEnvironment.environment.dialogmoteArbeidstakerUrl,
                namespace = externalMockEnvironment.environment.namespace,
                appname = externalMockEnvironment.environment.appname,
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                it("Setter ikke status paa gammelt møte med status FERDIGSTILT til LUKKET") {
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
                it("Setter ikke status paa gammelt møte med status AVLYST til LUKKET") {
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
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
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
