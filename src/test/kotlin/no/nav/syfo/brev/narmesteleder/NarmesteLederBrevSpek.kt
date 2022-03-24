package no.nav.syfo.brev.narmesteleder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerResponsDTO
import no.nav.syfo.brev.domain.BrevType
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

object NarmesteLederBrevSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(NarmesteLederBrevSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            // Add dummy deltakere so that id for deltaker and mote does not match by accident
            database.dropData()
            database.addDummyDeltakere()

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
            justRun { brukernotifikasjonProducer.sendDone(any(), any()) }

            val dineSykmeldteVarselProducer = mockk<DineSykmeldteVarselProducer>()
            justRun { dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>(relaxed = true)

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
                // Add dummy deltakere so that id for deltaker and mote does not match by accident
                database.addDummyDeltakere()
            }

            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
                val newDialogmoteDTOOther = generateNewDialogmoteDTO(
                    UserConstants.ARBEIDSTAKER_FNR,
                    virksomhetsnummer = UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
                )
                val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                val validTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = UserConstants.NARMESTELEDER_FNR.value,
                )
                val validTokenVeileder = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val incorrectTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = UserConstants.NARMESTELEDER_FNR_2.value,
                )

                it("Should return OK when les and response") {
                    val uuid: String
                    val createdDialogmoteUUID: String
                    val createdDialogmoteDeltakerArbeidsgiverUUID: String

                    with( // Create a dialogmote before we can test how to retrieve it
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        clearMocks(mqSenderMock)
                    }
                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        val dto = dialogmoteList.first()
                        dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        createdDialogmoteUUID = dto.uuid
                        createdDialogmoteDeltakerArbeidsgiverUUID = dto.arbeidsgiver.uuid
                    }

                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldBeNull()

                        uuid = narmesteLederBrevDTO.uuid
                    }

                    val urlNarmesteLederBrevUUIDLes =
                        "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiLesPath"

                    with(
                        handleRequest(HttpMethod.Post, urlNarmesteLederBrevUUIDLes) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(incorrectTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }

                    with(
                        handleRequest(HttpMethod.Post, urlNarmesteLederBrevUUIDLes) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldNotBeNull()
                    }
                    val urlNarmesteLederBrevUUIDRespons =
                        "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiResponsPath"

                    with(
                        handleRequest(HttpMethod.Post, urlNarmesteLederBrevUUIDRespons) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(
                                objectMapper.writeValueAsString(
                                    ArbeidstakerResponsDTO(
                                        svarType = DialogmoteSvarType.KOMMER.name,
                                        svarTekst = "Det passer bra - det/også <code>",
                                    )
                                )
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val narmesteLederBrevList =
                            objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        narmesteLederBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = narmesteLederBrevList.firstOrNull()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                    }
                    with(
                        handleRequest(HttpMethod.Get, "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarTekst shouldBeEqualTo "Det passer bra - det/også _code_"
                    }
                    // Repeated invocation should fail
                    with(
                        handleRequest(HttpMethod.Post, urlNarmesteLederBrevUUIDRespons) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(
                                objectMapper.writeValueAsString(
                                    ArbeidstakerResponsDTO(
                                        svarType = DialogmoteSvarType.KOMMER.name,
                                        svarTekst = "Det passer bra det fortsatt",
                                    )
                                )
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                    val pdfUrl = "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiPdfPath"
                    with(
                        handleRequest(HttpMethod.Get, pdfUrl) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = response.byteContent!!
                        pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfInnkalling
                    }
                    val urlMoteUUIDReferat =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val referatDto = generateNewReferatDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDReferat) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            setBody(objectMapper.writeValueAsString(referatDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val createdReferatArbeidsgiverBrevUUID: String
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList =
                            objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        val arbeidsgiverBrevDTO = arbeidsgiverBrevList.first()
                        arbeidsgiverBrevDTO.shouldNotBeNull()
                        arbeidsgiverBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidsgiverBrevDTO.lestDato.shouldBeNull()
                        arbeidsgiverBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        arbeidsgiverBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidsgiverUUID
                        arbeidsgiverBrevDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        val isCorrectDialogmotetid =
                            LocalDateTime.now().plusDays(29).isBefore(arbeidsgiverBrevDTO.tid)
                        isCorrectDialogmotetid shouldBeEqualTo true
                        createdReferatArbeidsgiverBrevUUID = arbeidsgiverBrevDTO.uuid
                    }
                    val urlReferatUUIDLes =
                        "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiLesPath"
                    with(
                        handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    val arbeidsgiverBrevDTO: NarmesteLederBrevDTO?
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList =
                            objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        arbeidsgiverBrevDTO = arbeidsgiverBrevList.firstOrNull()
                        arbeidsgiverBrevDTO.shouldNotBeNull()
                        arbeidsgiverBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidsgiverBrevDTO.lestDato.shouldNotBeNull()
                    }
                    with(
                        handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList =
                            objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        val arbeidstakerBrevUpdatedDTO = arbeidsgiverBrevList.firstOrNull()
                        arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                        arbeidstakerBrevUpdatedDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidsgiverBrevDTO!!.lestDato
                    }
                    val urlPdfForReferatNedlasting =
                        "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiPdfPath"
                    with(
                        handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = response.byteContent!!
                        pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfReferat
                    }
                    val urlMoteUUIDEndreReferat =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteEndreFerdigstiltPath"
                    val referatEndretDto = generateNewReferatDTO(
                        behandlerOppgave = "Dette er en en endring",
                        begrunnelseEndring = "Dette er en begrunnelse",
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDEndreReferat) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            setBody(objectMapper.writeValueAsString(referatEndretDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList =
                            objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        arbeidsgiverBrevList.size shouldBeEqualTo 3

                        val endretReferatBrevDTO = arbeidsgiverBrevList.firstOrNull()
                        endretReferatBrevDTO.shouldNotBeNull()
                        endretReferatBrevDTO.brevType shouldBeEqualTo BrevType.REFERAT_ENDRET.name
                    }
                }
                it("Same narmesteleder and arbeidstaker, different virksomhet") {
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        clearMocks(mqSenderMock)
                    }

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTOOther))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        clearMocks(mqSenderMock)
                    }

                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 2

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldBeNull()
                    }
                }
                it("Return OK when no brev exists") {
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 0
                    }
                }
            }
            describe("Error handling") {
                it("Return BAD REQUEST when $NAV_PERSONIDENT_HEADER is missing") {
                    val validTokenSelvbetjening = generateJWT(
                        audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                        subject = UserConstants.NARMESTELEDER_FNR.value,
                    )
                    with(
                        handleRequest(HttpMethod.Get, narmesteLederBrevApiBasePath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
