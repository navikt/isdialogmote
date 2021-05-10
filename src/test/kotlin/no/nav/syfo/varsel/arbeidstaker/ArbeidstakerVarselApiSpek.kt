package no.nav.syfo.varsel.arbeidstaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.varsel.arbeidstaker.domain.ArbeidstakerVarselDTO
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class ArbeidstakerVarselApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(ArbeidstakerVarselApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
            justRun { brukernotifikasjonProducer.sendDone(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>(relaxed = true)

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                startExternalMocks(
                    applicationMockMap = externalMockEnvironment.externalApplicationMockMap,
                    embeddedKafkaEnvironment = externalMockEnvironment.embeddedEnvironment,
                    embeddedRedisServer = externalMockEnvironment.redisServer,
                )
            }

            afterGroup {
                stopExternalMocks(
                    applicationMockMap = externalMockEnvironment.externalApplicationMockMap,
                    database = externalMockEnvironment.database,
                    embeddedKafkaEnvironment = externalMockEnvironment.embeddedEnvironment,
                    embeddedRedisServer = externalMockEnvironment.redisServer,
                )
            }

            describe("Les ArbeidstakerVarsel") {
                val validTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = ARBEIDSTAKER_FNR.value,
                )
                val validTokenVeileder = generateJWT(
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlArbeidstakerMoterList = arbeidstakerVarselApiPath

                    it("should return OK if request is successful") {
                        val createdArbeidstakerVarselUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)

                            arbeidstakerVarselList.size shouldBeEqualTo 1

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()

                            createdArbeidstakerVarselUUID = arbeidstakerVarselDTO.uuid
                        }

                        val urlArbeidstakerVarselUUIDLes = "$arbeidstakerVarselApiPath/$createdArbeidstakerVarselUUID$arbeidstakerVarselApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)
                            arbeidstakerVarselList.size shouldBeEqualTo 1

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldNotBeNull()
                            arbeidstakerVarselDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidstakerVarselDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid = LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendDone(any(), any()) }
                        }
                    }
                }
            }
        }
    }
})
