package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.client.person.oppfolgingstilfelle.toOppfolgingstilfellePerson
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.kOppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Assert.assertThrows
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class FerdigstillDialogmoteApiV2Spek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(FerdigstillDialogmoteApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

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

            describe("Ferdigstill Dialogmote") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO()

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
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
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                            dialogmoteDTO.referat shouldBeEqualTo null

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDFerdigstill = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.REFERAT, any()) }
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
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted

                            val referat = dialogmoteDTO.referat!!
                            referat.digitalt shouldBeEqualTo true
                            referat.situasjon shouldBeEqualTo "Dette er en beskrivelse av situasjonen"
                            referat.narmesteLederNavn shouldBeEqualTo "Grønn Bamse"
                            referat.document[0].type shouldBeEqualTo DocumentComponentType.HEADER
                            referat.document[0].texts shouldBeEqualTo listOf("Tittel referat")

                            referat.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            referat.document[1].texts shouldBeEqualTo listOf("Brødtekst")

                            referat.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            referat.document[2].key shouldBeEqualTo "Standardtekst"
                            referat.document[2].texts shouldBeEqualTo listOf("Dette er en standardtekst")

                            referat.andreDeltakere.first().funksjon shouldBeEqualTo "Verneombud"
                            referat.andreDeltakere.first().navn shouldBeEqualTo "Tøff Pyjamas"

                            referat.pdf shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfReferat

                            verify(exactly = 1) { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 2

                            moteStatusEndretList.forEach { moteStatusEndret ->
                                moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                                moteStatusEndret.tilfelleStart shouldBeEqualTo kOppfolgingstilfellePersonDTO().toOppfolgingstilfellePerson().fom
                            }
                        }

                        assertThrows(RuntimeException::class.java) {
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        }.message shouldBeEqualTo "Failed to Ferdigstille Dialogmote, already Ferdigstilt"

                        val urlMoteUUIDAvlys = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTO()
                        assertThrows(RuntimeException::class.java) {
                            handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                            }
                        }.message shouldBeEqualTo "Failed to Avlys Dialogmote: already Ferdigstilt"

                        val urlMoteUUIDPostTidSted = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                        val endreTidStedDialogMoteDto = generateEndreDialogmoteTidStedDTO()
                        assertThrows(RuntimeException::class.java) {
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(endreTidStedDialogMoteDto))
                            }
                        }.message shouldBeEqualTo "Failed to change tid/sted, already Ferdigstilt"
                    }
                }
                describe("Happy path: ferdigstilling gjøres av annen bruker enn den som gjør innkalling") {
                    val validToken2 = generateJWT(
                        externalMockEnvironment.environment.aadAppClient,
                        externalMockEnvironment.wellKnownVeilederV2.issuer,
                        VEILEDER_IDENT_2,
                    )
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO()

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
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
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT
                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
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
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT_2
                        }
                    }
                }
            }
        }
    }
})
