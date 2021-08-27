package no.nav.syfo.brev.arbeidstaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteAvlysPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.generator.generateAvlysDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class ArbeidstakerBrevApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(ArbeidstakerBrevApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            // Add dummy deltakere so that id for deltaker and mote does not match by accident
            database.addDummyDeltakere()

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
                database.addDummyDeltakere()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            describe("Les ArbeidstakerBrev") {
                val validTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = ARBEIDSTAKER_FNR.value,
                )
                val validTokenVeileder = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

                    it("should return OK if request is successful") {
                        val createdArbeidstakerBrevUUID: String

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

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO!!.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid = LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendDone(any(), any()) }
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
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

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendDone(any(), any()) }
                        }
                    }
                }
                describe("Happy path med mer enn et møte for aktuell person") {
                    val newDialogmoteAvlyst1 =
                        generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 1", LocalDateTime.now().plusDays(10))
                    val newDialogmoteAvlyst2 =
                        generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 2", LocalDateTime.now().plusDays(20))
                    val newDialogmoteInnkalt =
                        generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 3", LocalDateTime.now().plusDays(30))

                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID = ""
                    var createdDialogmoteDeltakerArbeidstakerUUID = ""

                    it("should return OK if request is successful") {
                        for (
                            dialogmoteDTO in listOf(
                                newDialogmoteAvlyst1,
                                newDialogmoteAvlyst2,
                                newDialogmoteInnkalt
                            )
                        ) {
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
                                val urlMoteUUIDAvlys =
                                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
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
                        val createdArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 5

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 5

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

                            arbeidstakerBrevDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid =
                                LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                        }

                        val urlMoteUUIDReferat =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
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

                        val createdReferatArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()
                            arbeidstakerBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID
                            arbeidstakerBrevDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid =
                                LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                            createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }
                        val urlReferatUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                            arbeidstakerBrevUpdatedDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }
                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfInnkallingArbeidstaker
                        }
                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfReferat
                        }
                    }
                }
                describe("Uautorisert person nektes tilgang") {
                    val newDialogmoteInnkalt =
                        generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted", LocalDateTime.now().plusDays(30))

                    val validTokenSelvbetjeningAnnenPerson = generateJWT(
                        audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                        subject = ARBEIDSTAKER_ANNEN_FNR.value,
                    )

                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID: String

                    it("should return Forbidden when bearer header contains token for unauthorized person") {
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteInnkalt))
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
                        }

                        val createdArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerVarselUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 0
                        }

                        val urlMoteUUIDReferat =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
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

                        val createdReferatArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 2

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }
                        val urlReferatUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfInnkallingArbeidstaker
                        }

                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfReferat
                        }
                    }
                }
            }
        }
    }
})
