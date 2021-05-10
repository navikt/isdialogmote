package no.nav.syfo.cronjob.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.mock.*
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

            val azureADMock = AzureADMock()
            val dokarkivMock = DokarkivMock()
            val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
            val modiasyforestMock = ModiasyforestMock()
            val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
            val syfomoteadminMock = SyfomoteadminMock()
            val syfopersonMock = SyfopersonMock()
            val tilgangskontrollMock = VeilederTilgangskontrollMock()

            val externalApplicationMockMap = hashMapOf(
                dokarkivMock.name to dokarkivMock.server,
                isdialogmotepdfgenMock.name to isdialogmotepdfgenMock.server,
                modiasyforestMock.name to modiasyforestMock.server,
                syfobehandlendeenhetMock.name to syfobehandlendeenhetMock.server,
                syfomoteadminMock.name to syfomoteadminMock.server,
                syfopersonMock.name to syfopersonMock.server,
                tilgangskontrollMock.name to tilgangskontrollMock.server,
            )

            val applicationState = testAppState()

            val database = TestDatabase()

            val embeddedEnvironment = testKafka()

            val environment = testEnvironment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
                dokarkivUrl = dokarkivMock.url,
                isdialogmotepdfgenUrl = isdialogmotepdfgenMock.url,
                modiasyforestUrl = modiasyforestMock.url,
                syfobehandlendeenhetUrl = syfobehandlendeenhetMock.url,
                syfomoteadminUrl = syfomoteadminMock.url,
                syfopersonUrl = syfopersonMock.url,
                syfotilgangskontrollUrl = tilgangskontrollMock.url
            )

            val redisServer = testRedis(environment)

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
            val wellKnownVeileder = wellKnownSelvbetjeningMock()

            application.apiModule(
                applicationState = applicationState,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                database = database,
                mqSender = mqSenderMock,
                environment = environment,
                wellKnownSelvbetjening = wellKnownSelvbetjening,
                wellKnownVeileder = wellKnownVeileder,
            )

            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val dialogmotedeltakerVarselJournalforingService = DialogmotedeltakerVarselJournalforingService(
                database = database,
            )
            val azureAdClient = mockk<AzureAdClient>()
            coEvery {
                azureAdClient.getAccessTokenForResource(environment.dokarkivClientId)
            } returns azureADMock.aadAccessToken
            val dokarkivClient = DokarkivClient(
                azureAdClient = azureAdClient,
                dokarkivClientId = environment.dokarkivClientId,
                dokarkivBaseUrl = dokarkivMock.url,
            )
            val leaderPodClient = mockk<LeaderPodClient>()

            val dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
                applicationState = applicationState,
                dialogmotedeltakerVarselJournalforingService = dialogmotedeltakerVarselJournalforingService,
                dokarkivClient = dokarkivClient,
                leaderPodClient = leaderPodClient,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                startExternalMocks(
                    applicationMockMap = externalApplicationMockMap,
                    embeddedKafkaEnvironment = embeddedEnvironment,
                    embeddedRedisServer = redisServer,
                )
            }

            afterGroup {
                stopExternalMocks(
                    applicationMockMap = externalApplicationMockMap,
                    database = database,
                    embeddedKafkaEnvironment = embeddedEnvironment,
                    embeddedRedisServer = redisServer,
                )
            }

            describe("Journalfor ArbeidstakerVarsel with type Innkalling") {
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnownVeileder.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"

                it("should update journalpost") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

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

                    runBlocking {
                        val result = dialogmoteVarselJournalforingCronjob.dialogmoteVarselJournalforingJob()

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
