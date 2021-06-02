package no.nav.syfo.cronjob.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import no.nav.syfo.dialogmote.ReferatJournalforingService
import no.nav.syfo.dialogmote.api.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
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

            val dialogmotedeltakerVarselJournalforingService = DialogmotedeltakerVarselJournalforingService(
                database = database,
            )
            val referatJournalforingService = ReferatJournalforingService(
                database = database,
            )
            val azureAdClient = mockk<AzureAdClient>()
            coEvery {
                azureAdClient.getAccessTokenForResource(externalMockEnvironment.environment.dokarkivClientId)
            } returns externalMockEnvironment.azureADMock.aadAccessToken
            val dokarkivClient = DokarkivClient(
                azureAdClient = azureAdClient,
                dokarkivClientId = externalMockEnvironment.environment.dokarkivClientId,
                dokarkivBaseUrl = externalMockEnvironment.dokarkivMock.url,
            )
            val leaderPodClient = mockk<LeaderPodClient>()

            val dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
                applicationState = externalMockEnvironment.applicationState,
                dialogmotedeltakerVarselJournalforingService = dialogmotedeltakerVarselJournalforingService,
                referatJournalforingService = referatJournalforingService,
                dokarkivClient = dokarkivClient,
                leaderPodClient = leaderPodClient,
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
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val urlMote = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"

                it("should update journalpost") {
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
                        verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
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

                    val urlMoteUUIDPostTidSted = "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
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
                        "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
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

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
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
                        result.updated shouldBeEqualTo 3
                    }
                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.referatJournalforingJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
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
