package no.nav.syfo.dialogmote

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PostDialogmoteApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmoteApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            val mqSenderMock = mockk<MQSenderInterface>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"

            describe("Create Dialogmote for PersonIdent payload") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    VEILEDER_IDENT,
                )

                beforeEachTest {
                    clearMocks(brukernotifikasjonProducer)
                    justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                    clearMocks(mqSenderMock)
                    justRun { mqSenderMock.sendMQMessage(any(), any()) }
                }

                afterEachTest {
                    database.dropData()
                }

                describe("Happy path") {

                    val urlMoter = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

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
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidstaker"

                            arbeidstakerVarselDTO.document.size shouldBeEqualTo 5
                            arbeidstakerVarselDTO.document[0].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[0].title shouldBeEqualTo "Tittel innkalling"
                            arbeidstakerVarselDTO.document[0].texts shouldBeEqualTo emptyList()
                            arbeidstakerVarselDTO.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[1].title shouldBeEqualTo "Møtetid:"
                            arbeidstakerVarselDTO.document[1].texts shouldBeEqualTo listOf("5. mai 2021")
                            arbeidstakerVarselDTO.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[2].texts shouldBeEqualTo listOf("Brødtekst")
                            arbeidstakerVarselDTO.document[3].type shouldBeEqualTo DocumentComponentType.LINK
                            arbeidstakerVarselDTO.document[3].texts shouldBeEqualTo listOf("https://nav.no/")
                            arbeidstakerVarselDTO.document[4].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[4].texts shouldBeEqualTo listOf("Vennlig hilsen", "NAV Staden", "Kari Saksbehandler")

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidsgiver.varselList.size shouldBeEqualTo 1
                            val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidsgiver"

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 1

                            moteStatusEndretList.first().status.name shouldBeEqualTo dialogmoteDTO.status
                            moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                            moteStatusEndretList.first().tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO.fom
                        }
                    }

                    it("should return OK if request is successful: optional values missing") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

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
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo ""

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo ""

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        }
                    }
                }

                describe("Unhappy paths") {
                    val url = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied access to person") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied person has Adressbeskyttese") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_ADRESSEBESKYTTET)
                        val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied person has cannot receive digital documents") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_IKKE_VARSEL)
                        val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status InternalServerError if denied person with Dialogmote with Virksomhet does not have a leader for that Virksomhet") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
                        val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return Forbidden if requesting to create Dialogmote for PersonIdent with an existing unfinished Dialogmote") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                        with(
                            handleRequest(HttpMethod.Post, url) {
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
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
                        }
                    }
                }
            }
        }
    }
})
