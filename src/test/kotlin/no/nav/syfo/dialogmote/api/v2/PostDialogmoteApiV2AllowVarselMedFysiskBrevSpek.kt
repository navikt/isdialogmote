package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v1.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PostDialogmoteApiV2AllowVarselMedFysiskBrevSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmoteApiV2AllowVarselMedFysiskBrevSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment(allowVarselMedFysiskBrev = true)
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

            val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

            describe("Create Dialogmote for PersonIdent payload") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
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

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful even if ikke-varsle") {
                        val moteTidspunkt = LocalDateTime.now().plusDays(30)
                        val moteTidspunktString = moteTidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        val newDialogmoteDTO = generateNewDialogmoteDTO(
                            personIdentNumber = ARBEIDSTAKER_IKKE_VARSEL,
                            dato = moteTidspunkt,
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val xmlStringSlot = slot<String>()
                            verify(exactly = 1) {
                                mqSenderMock.sendMQMessage(
                                    MotedeltakerVarselType.INNKALT,
                                    capture(xmlStringSlot)
                                )
                            }
                            val xml = xmlStringSlot.captured
                            xml.shouldContain("<kanal>EPOST</kanal><kontaktinformasjon>narmesteLederNavn@gmail.com</kontaktinformasjon>")
                            xml.shouldContain("<orgnummer>912345678</orgnummer>")
                            xml.shouldContain("<parameterListe><key>navn</key><value>narmesteLederNavn</value></parameterListe>")
                            xml.shouldContain("<parameterListe><key>tidspunkt</key><value>$moteTidspunktString</value></parameterListe>")
                            clearMocks(mqSenderMock)
                        }
                        val varselUuid: String
                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_IKKE_VARSEL.value)
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
                            varselUuid = arbeidstakerVarselDTO.uuid
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo false
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
                            arbeidstakerVarselDTO.document[4].texts shouldBeEqualTo listOf(
                                "Vennlig hilsen",
                                "NAV Staden",
                                "Kari Saksbehandler"
                            )
                            arbeidstakerVarselDTO.brevBestiltTidspunkt shouldBeEqualTo null

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidsgiver.varselList.size shouldBeEqualTo 1
                            val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidsgiver"

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"

                            verify(exactly = 0) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 1

                            moteStatusEndretList.first().status.name shouldBeEqualTo dialogmoteDTO.status
                            moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                            moteStatusEndretList.first().tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO.fom
                        }
                        database.setMotedeltakerArbeidstakerVarselBrevBestilt(varselUuid, "123")
                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_IKKE_VARSEL.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.brevBestiltTidspunkt shouldNotBe null
                            arbeidstakerVarselDTO.brevBestiltTidspunkt!!.toLocalDate() shouldBeEqualTo LocalDate.now()
                        }
                    }

                    it("should return OK if request is successful: optional values missing") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_IKKE_VARSEL)

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
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_IKKE_VARSEL.value)
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
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo false
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo ""

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo ""

                            verify(exactly = 0) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        }
                    }
                }
            }
        }
    }
})
