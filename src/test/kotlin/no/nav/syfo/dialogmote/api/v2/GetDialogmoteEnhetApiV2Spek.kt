package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GetDialogmoteEnhetApiV2Spek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(GetDialogmoteEnhetApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val dineSykmeldteVarselProducer = mockk<DineSykmeldteVarselProducer>()
            justRun { dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
                mqSenderMock = mqSenderMock,
                altinnMock = altinnMock,
            )

            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                justRun { mqSenderMock.sendMQMessage(any(), any()) }
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                justRun { dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(any(), any()) }
                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
                database.dropData()
            }

            describe("Get Dialogmoter for EnhetNr") {
                val urlEnhetAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR.value}"
                val urlEnhetAccessIncludeHistoriske = "$urlEnhetAccess?inkluderHistoriske=true"
                val urlEnhetNoAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR_NO_ACCESS.value}"
                val validTokenV2 = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

                    it("should return DialogmoteList with unfinished dialogmote if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
                        }

                        val newDialogmoteAdressebeskyttet = generateNewDialogmote(ARBEIDSTAKER_ADRESSEBESKYTTET)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteAdressebeskyttet
                            )
                        }
                        val newDialogmoteFerdigstilt =
                            generateNewDialogmote(ARBEIDSTAKER_FNR, status = DialogmoteStatus.FERDIGSTILT)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteFerdigstilt
                            )
                        }
                        val newDialogmoteAvlyst =
                            generateNewDialogmote(ARBEIDSTAKER_FNR, status = DialogmoteStatus.AVLYST)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteAvlyst
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlEnhetAccess) {
                                addHeader(Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        }
                    }

                    it("should return DialogmoteList with all dialogmoter if request with inkluderHistoriske parameter is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
                        }

                        val newDialogmoteAdressebeskyttet = generateNewDialogmote(ARBEIDSTAKER_ADRESSEBESKYTTET)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteAdressebeskyttet
                            )
                        }
                        val newDialogmoteFerdigstilt =
                            generateNewDialogmote(ARBEIDSTAKER_FNR, status = DialogmoteStatus.FERDIGSTILT)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteFerdigstilt
                            )
                        }
                        val newDialogmoteAvlyst =
                            generateNewDialogmote(ARBEIDSTAKER_FNR, status = DialogmoteStatus.AVLYST)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteAvlyst
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlEnhetAccessIncludeHistoriske) {
                                addHeader(Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 3
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.INNKALT.name } shouldBeEqualTo true
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.AVLYST.name } shouldBeEqualTo true
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.FERDIGSTILT.name } shouldBeEqualTo true
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlEnhetAccess) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied access to Enhet") {
                        with(
                            handleRequest(HttpMethod.Get, urlEnhetNoAccess) {
                                addHeader(Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }
                }
            }
        }
    }
})
