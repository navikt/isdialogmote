package no.nav.syfo.brev.narmesteleder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NarmesteLederBrevSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(NarmesteLederBrevSpek::class.java.simpleName) {
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
                // Add dummy deltakere so that id for deltaker and mote does not match by accident
                database.addDummyDeltakere()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
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

                it("Should return OK") {
                    val uuid: String
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

                    val pdfUrl = "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiPdfPath"
                    with(
                        handleRequest(HttpMethod.Get, pdfUrl) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = response.byteContent!!
                        pdfContent shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfInnkallingArbeidsgiver
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
