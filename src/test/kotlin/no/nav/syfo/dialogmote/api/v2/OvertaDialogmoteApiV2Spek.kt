package no.nav.syfo.dialogmote.api.v2

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
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
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

            val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock,
            )

            beforeEachGroup {
                database.dropData()
            }
            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            describe("Overta Dialogmoter") {
                val validTokenV2 = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val validTokenV2AnnenVeileder = generateJWTNavIdent(
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
