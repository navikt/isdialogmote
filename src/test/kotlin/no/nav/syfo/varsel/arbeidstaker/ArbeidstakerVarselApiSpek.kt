package no.nav.syfo.varsel.arbeidstaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.dialogmote.api.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
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
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
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
                describe("Happy path med mer enn et møte for aktuell person") {
                    val newDialogmoteAvlyst1 = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 1", LocalDateTime.now().plusDays(10))
                    val newDialogmoteAvlyst2 = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 2", LocalDateTime.now().plusDays(20))
                    val newDialogmoteInnkalt = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 3", LocalDateTime.now().plusDays(30))

                    val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID = ""
                    var createdDialogmoteDeltakerArbeidstakerUUID = ""

                    it("should return OK if request is successful") {
                        for (dialogmoteDTO in listOf(newDialogmoteAvlyst1, newDialogmoteAvlyst2, newDialogmoteInnkalt)) {
                            with(
                                handleRequest(HttpMethod.Post, urlMote) {
                                    addHeader(Authorization, bearerHeader(validTokenVeileder))
                                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                    setBody(objectMapper.writeValueAsString(dialogmoteDTO))
                                }
                            ) {
                                response.status() shouldBeEqualTo HttpStatusCode.OK
                            }

                            with(
                                handleRequest(HttpMethod.Get, urlMote) {
                                    addHeader(Authorization, bearerHeader(validTokenVeileder))
                                    addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                                }
                            ) {
                                response.status() shouldBeEqualTo HttpStatusCode.OK

                                val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                                val dto = dialogmoteList.first()
                                dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                                createdDialogmoteUUID = dto.uuid
                                createdDialogmoteDeltakerArbeidstakerUUID = dto.arbeidstaker.uuid
                            }
                            if (dialogmoteDTO != newDialogmoteInnkalt) {
                                val urlMoteUUIDAvlys = "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                                val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                                with(
                                    handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                        addHeader(Authorization, bearerHeader(validTokenVeileder))
                                        setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                                    }
                                ) {
                                    response.status() shouldBeEqualTo HttpStatusCode.OK
                                }
                            }
                        }
                        val createdArbeidstakerVarselUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerVarselApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)

                            arbeidstakerVarselList.size shouldBeEqualTo 5

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

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
                            handleRequest(HttpMethod.Get, arbeidstakerVarselApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)
                            arbeidstakerVarselList.size shouldBeEqualTo 5

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldNotBeNull()
                            arbeidstakerVarselDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

                            arbeidstakerVarselDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid = LocalDateTime.now().plusDays(29).isBefore(arbeidstakerVarselDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                        }

                        val urlMoteUUIDReferat = "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        val referatDto = generateNewReferatDTO()
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDReferat) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                setBody(objectMapper.writeValueAsString(referatDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        val createdReferatArbeidstakerVarselUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerVarselApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)
                            arbeidstakerVarselList.size shouldBeEqualTo 6

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID
                            arbeidstakerVarselDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid = LocalDateTime.now().plusDays(29).isBefore(arbeidstakerVarselDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                            createdReferatArbeidstakerVarselUUID = arbeidstakerVarselDTO.uuid
                        }
                        val urlReferatUUIDLes = "$arbeidstakerVarselApiPath/$createdReferatArbeidstakerVarselUUID$arbeidstakerVarselApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerVarselApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerVarselList = objectMapper.readValue<List<ArbeidstakerVarselDTO>>(response.content!!)
                            arbeidstakerVarselList.size shouldBeEqualTo 6

                            val arbeidstakerVarselDTO = arbeidstakerVarselList.first()
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerVarselDTO.lestDato.shouldNotBeNull()
                        }
                    }
                }
            }
        }
    }
})
