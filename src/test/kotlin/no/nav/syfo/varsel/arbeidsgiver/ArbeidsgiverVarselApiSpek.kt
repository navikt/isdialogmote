package no.nav.syfo.varsel.arbeidsgiver

import no.nav.syfo.varsel.narmesteleder.domain.NarmestelederBrevDTO
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
import no.nav.syfo.testhelper.UserConstants.ARBEIDSGIVER_FNR
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class ArbeidsgiverVarselApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(ArbeidsgiverVarselApiSpek::class.java.simpleName) {

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

            describe("Les ArbeidsgiverVarsel") {
                val validTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = ARBEIDSGIVER_FNR.value,
                )
                val validTokenVeileder = generateJWT(
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSGIVER_FNR)
                    val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlArbeidsgiverMoterList = narmestelederBrevApiPath

                    it("should return OK if request is successful") {
                        val createdArbeidsgiverVarselUUID: String

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
                            handleRequest(HttpMethod.Get, urlArbeidsgiverMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)

                            arbeidsgiverVarselList.size shouldBeEqualTo 1

                            val arbeidsgiverVarselDTO = arbeidsgiverVarselList.first()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()

                            createdArbeidsgiverVarselUUID = arbeidsgiverVarselDTO.uuid
                        }

                        val urlArbeidsgiverVarselUUIDLes = "$narmestelederBrevApiPath/$createdArbeidsgiverVarselUUID$narmestelederBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidsgiverVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidsgiverVarselDTO: NarmestelederBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidsgiverMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 1

                            arbeidsgiverVarselDTO = arbeidsgiverVarselList.firstOrNull()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO!!.lestDato.shouldNotBeNull()
                            arbeidsgiverVarselDTO!!.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidsgiverVarselDTO!!.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid = LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendDone(any(), any()) }
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidsgiverVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidsgiverMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 1

                            val arbeidsgiverVarselUpdatedDTO = arbeidsgiverVarselList.first()
                            arbeidsgiverVarselUpdatedDTO.lestDato shouldBeEqualTo arbeidsgiverVarselDTO!!.lestDato
                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendDone(any(), any()) }
                        }
                    }
                }
                describe("Happy path med mer enn et møte for aktuell person") {
                    val newDialogmoteAvlyst1 = generateNewDialogmoteDTO(ARBEIDSGIVER_FNR, "Sted 1", LocalDateTime.now().plusDays(10))
                    val newDialogmoteAvlyst2 = generateNewDialogmoteDTO(ARBEIDSGIVER_FNR, "Sted 2", LocalDateTime.now().plusDays(20))
                    val newDialogmoteInnkalt = generateNewDialogmoteDTO(ARBEIDSGIVER_FNR, "Sted 3", LocalDateTime.now().plusDays(30))

                    val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID = ""
                    var createdDialogmoteDeltakerArbeidsgiverUUID = ""

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
                                    addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSGIVER_FNR.value)
                                }
                            ) {
                                response.status() shouldBeEqualTo HttpStatusCode.OK

                                val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                                val dto = dialogmoteList.first()
                                dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                                createdDialogmoteUUID = dto.uuid
                                createdDialogmoteDeltakerArbeidsgiverUUID = dto.arbeidsgiver.uuid
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
                        val createdArbeidsgiverVarselUUID: String
                        with(
                            handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)

                            arbeidsgiverVarselList.size shouldBeEqualTo 5

                            val arbeidsgiverVarselDTO = arbeidsgiverVarselList.first()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidsgiverUUID

                            createdArbeidsgiverVarselUUID = arbeidsgiverVarselDTO.uuid
                        }

                        val urlArbeidsgiverVarselUUIDLes = "$narmestelederBrevApiPath/$createdArbeidsgiverVarselUUID$narmestelederBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidsgiverVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 5

                            val arbeidsgiverVarselDTO = arbeidsgiverVarselList.first()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidsgiverVarselDTO.lestDato.shouldNotBeNull()
                            arbeidsgiverVarselDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidsgiverVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidsgiverUUID

                            arbeidsgiverVarselDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid = LocalDateTime.now().plusDays(29).isBefore(arbeidsgiverVarselDTO.tid)
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

                        val createdReferatArbeidsgiverVarselUUID: String
                        with(
                            handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 6

                            val arbeidsgiverVarselDTO = arbeidsgiverVarselList.first()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidsgiverVarselDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidsgiverUUID
                            arbeidsgiverVarselDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid = LocalDateTime.now().plusDays(29).isBefore(arbeidsgiverVarselDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                            createdReferatArbeidsgiverVarselUUID = arbeidsgiverVarselDTO.uuid
                        }
                        val urlReferatUUIDLes = "$narmestelederBrevApiPath/$createdReferatArbeidsgiverVarselUUID$narmestelederBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        var arbeidsgiverVarselDTO: NarmestelederBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 6

                            arbeidsgiverVarselDTO = arbeidsgiverVarselList.firstOrNull()
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO!!.varselType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidsgiverVarselDTO!!.lestDato.shouldNotBeNull()
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidsgiverVarselList = objectMapper.readValue<List<NarmestelederBrevDTO>>(response.content!!)
                            arbeidsgiverVarselList.size shouldBeEqualTo 6

                            val arbeidsgiverVarselUpdatedDTO = arbeidsgiverVarselList.firstOrNull()
                            arbeidsgiverVarselUpdatedDTO.shouldNotBeNull()
                            arbeidsgiverVarselUpdatedDTO.varselType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidsgiverVarselUpdatedDTO.lestDato shouldBeEqualTo arbeidsgiverVarselDTO!!.lestDato
                        }
                    }
                }
            }
        }
    }
})
