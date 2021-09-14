package no.nav.syfo.cronjob.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.dialogmote.ReferatJournalpostService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.api.v2.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmoteVarselJournalforingCronjobSpek : Spek({

    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(DialogmoteVarselJournalforingCronjobSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            val dialogmotedeltakerVarselJournalpostService = DialogmotedeltakerVarselJournalpostService(
                database = database,
            )
            val referatJournalpostService = ReferatJournalpostService(
                database = database,
            )
            val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)

            val dokarkivClient = DokarkivClient(
                azureAdV2Client = azureAdV2Client,
                dokarkivClientId = externalMockEnvironment.environment.dokarkivClientId,
                dokarkivBaseUrl = externalMockEnvironment.dokarkivMock.url,
            )

            val pdlClient = PdlClient(
                azureAdV2Client = azureAdV2Client,
                pdlClientId = externalMockEnvironment.environment.pdlClientId,
                pdlUrl = externalMockEnvironment.pdlMock.url,
            )

            val dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
                dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
                referatJournalpostService = referatJournalpostService,
                dokarkivClient = dokarkivClient,
                pdlClient = pdlClient,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            describe("Journalfor ArbeidstakerVarsel with type Innkalling") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                it("should update journalpost when ferdigstilt") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
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

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDPostTidSted = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
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

                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.dialogmoteVarselJournalforingJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 2
                    }
                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.referatJournalforingJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                }

                it("should update journalpost when avlyst") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
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

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDPostTidSted = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
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

                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                    val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.dialogmoteVarselJournalforingJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 3
                    }
                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.referatJournalforingJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                }

                it("should fail to update journalpost if no JournalpostId is found") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_NO_JOURNALFORING)

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
                        val result = dialogmoteVarselJournalforingCronjob.dialogmoteVarselJournalforingJob()

                        result.failed shouldBeEqualTo 1
                        result.updated shouldBeEqualTo 0
                    }
                }
            }
        }
    }
})
