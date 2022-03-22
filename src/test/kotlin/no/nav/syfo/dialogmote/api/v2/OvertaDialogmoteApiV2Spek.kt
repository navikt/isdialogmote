package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.justRun
import io.mockk.mockk
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.OvertaDialogmoterDTO
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OvertaDialogmoteApiV2Spek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(OvertaDialogmoteApiV2Spek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            beforeEachGroup {
                database.dropData()
            }
            beforeEachTest {
                justRun { mqSenderMock.sendMQMessage(any(), any()) }
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
            }

            describe("Overta Dialogmoter") {
                val validTokenV2 = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val validTokenV2AnnenVeileder = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT_2,
                )
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
                val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR.value}"
                val urlOvertaMoter = "$dialogmoteApiV2Basepath$dialogmoteActionsApiOvertaPath"
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                val newDialogmoteDTOAnnenArbeidstaker = generateNewDialogmoteDTO(ARBEIDSTAKER_ANNEN_FNR)

                describe("Happy path") {
                    it("should overta Dialogmoter if request is successfull") {
                        val createdDialogmoterUuids = mutableListOf<String>()

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2AnnenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTOAnnenArbeidstaker))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 2
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT } shouldBeEqualTo true
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT_2 } shouldBeEqualTo true

                            createdDialogmoterUuids.addAll(dialogmoteList.map { it.uuid })
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlOvertaMoter) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(OvertaDialogmoterDTO(createdDialogmoterUuids)))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 2
                            dialogmoteList.all { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT } shouldBeEqualTo true
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlOvertaMoter) {
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status Bad Request if no dialogmoteUuids supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlOvertaMoter) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(OvertaDialogmoterDTO(emptyList())))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status Forbidden if denied access to dialogmøte person") {
                        val createdDialogmoterUuids = mutableListOf<String>()

                        val newDialogmoteNoVeilederAccess =
                            generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        database.connection.use { connection ->
                            val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteNoVeilederAccess
                            )
                            val dialogmoteNoAccessUuid = (dialogmoteIdPair.second).toString()
                            createdDialogmoterUuids.add(dialogmoteNoAccessUuid)
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlOvertaMoter) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(OvertaDialogmoterDTO(createdDialogmoterUuids)))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if contains dialogmøte with denied access to person") {
                        val createdDialogmoterUuids = mutableListOf<String>()

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2AnnenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            createdDialogmoterUuids.addAll(dialogmoteList.map { it.uuid })
                        }

                        val newDialogmoteNoVeilederAccess =
                            generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        database.connection.use { connection ->
                            val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteNoVeilederAccess
                            )
                            val dialogmoteNoAccessUuid = (dialogmoteIdPair.second).toString()
                            createdDialogmoterUuids.add(dialogmoteNoAccessUuid)
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlOvertaMoter) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenV2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(OvertaDialogmoterDTO(createdDialogmoterUuids)))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
