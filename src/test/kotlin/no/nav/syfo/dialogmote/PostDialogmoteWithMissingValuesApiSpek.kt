package no.nav.syfo.dialogmote

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.*
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PostDialogmoteWithMissingValuesApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmoteWithMissingValuesApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
            val modiasyforestMock = ModiasyforestMock()
            val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
            val syfomoteadminMock = SyfomoteadminMock()
            val syfopersonMock = SyfopersonMock()
            val tilgangskontrollMock = VeilederTilgangskontrollMock()

            val applicationState = testAppState()

            val database = TestDatabase()

            val embeddedEnvironment = KafkaEnvironment(
                autoStart = false,
                withSchemaRegistry = false,
                topicNames = listOf(
                    BRUKERNOTIFIKASJON_OPPGAVE_TOPIC,
                    BRUKERNOTIFIKASJON_DONE_TOPIC,
                )
            )

            val environment = testEnvironment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
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

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                isdialogmotepdfgenMock.server.start()
                modiasyforestMock.server.start()
                syfobehandlendeenhetMock.server.start()
                syfomoteadminMock.server.start()
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()

                embeddedEnvironment.start()
                redisServer.start()
            }

            afterGroup {
                isdialogmotepdfgenMock.server.stop(1L, 10L)
                modiasyforestMock.server.stop(1L, 10L)
                syfobehandlendeenhetMock.server.stop(1L, 10L)
                syfomoteadminMock.server.stop(1L, 10L)
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)

                database.stop()
                embeddedEnvironment.tearDown()
                redisServer.stop()
            }

            describe("Create Dialogmote for PersonIdent payload") {
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnownVeileder.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)
                    val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlMoter = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"
                    it("should return OK if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.planlagtMoteUuid.shouldBeNull()
                            dialogmoteDTO.planlagtMoteBekreftetTidspunkt.shouldBeNull()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1
                            dialogmoteDTO.arbeidstaker.fritekstInnkalling shouldBeEqualTo ""

                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.pdf shouldBeEqualTo isdialogmotepdfgenMock.pdfInnkallingArbeidstaker
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidsgiver.fritekstInnkalling shouldBeEqualTo ""

                            dialogmoteDTO.tidStedList.size shouldBeEqualTo 1
                            val dialogmoteTidStedDTO = dialogmoteDTO.tidStedList.first()
                            dialogmoteTidStedDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteTidStedDTO.videoLink shouldBeEqualTo ""

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        }
                    }
                }
            }
        }
    }
})
