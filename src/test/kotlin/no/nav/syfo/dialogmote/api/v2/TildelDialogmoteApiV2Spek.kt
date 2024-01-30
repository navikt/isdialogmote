package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
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
import no.nav.syfo.dialogmote.api.domain.TildelDialogmoterDTO
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class TildelDialogmoteApiV2Spek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(TildelDialogmoteApiV2Spek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            val veilederCallerIdent = UserConstants.VEILEDER_IDENT
            val veilederIdentTildelesMoter = UserConstants.VEILEDER_IDENT_2
            val veilederCallerToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                veilederCallerIdent,
            )

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
            afterGroup { database.dropData() }

            describe("Tildel dialogmoter") {
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
                val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${UserConstants.ENHET_NR.value}"
                val urlTildelMote = "$dialogmoteApiV2Basepath$dialogmoteTildelPath"
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
                val newDialogmoteDTOAnnenArbeidstaker = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_ANNEN_FNR)

                describe("Happy path") {
                    it("should tildele dialogmoter if request is successful") {
                        val createdDialogmoterUuids = mutableListOf<UUID>()

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTOAnnenArbeidstaker))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 2
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederCallerIdent } shouldBeEqualTo true
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederIdentTildelesMoter } shouldBeEqualTo false

                            createdDialogmoterUuids.addAll(dialogmoteList.map { UUID.fromString(it.uuid) })
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlTildelMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        TildelDialogmoterDTO(
                                            veilederIdent = veilederIdentTildelesMoter,
                                            dialogmoteUuids = createdDialogmoterUuids
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 2
                            dialogmoteList.all { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederIdentTildelesMoter } shouldBeEqualTo true
                            dialogmoteList.all { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederCallerIdent } shouldBeEqualTo false
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlTildelMote) {
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status Bad Request if no dialogmoteUuids supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlTildelMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        TildelDialogmoterDTO(
                                            veilederIdent = veilederIdentTildelesMoter,
                                            dialogmoteUuids = emptyList()
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status Forbidden if denied access to dialogmøte person") {
                        val createdDialogmoterUuids = mutableListOf<UUID>()

                        val newDialogmoteNoVeilederAccess =
                            generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        database.connection.use { connection ->
                            val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteNoVeilederAccess
                            )
                            createdDialogmoterUuids.add(dialogmoteIdPair.second)
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlTildelMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        TildelDialogmoterDTO(
                                            veilederIdent = veilederIdentTildelesMoter,
                                            dialogmoteUuids = createdDialogmoterUuids,
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if contains dialogmøte with denied access to person") {
                        val createdDialogmoterUuids = mutableListOf<UUID>()

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoterEnhet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            createdDialogmoterUuids.addAll(dialogmoteList.map { UUID.fromString(it.uuid) })
                        }

                        val newDialogmoteNoVeilederAccess =
                            generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        database.connection.use { connection ->
                            val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmoteNoVeilederAccess
                            )
                            createdDialogmoterUuids.add(dialogmoteIdPair.second)
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlTildelMote) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(veilederCallerToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        TildelDialogmoterDTO(
                                            veilederIdent = veilederIdentTildelesMoter,
                                            dialogmoteUuids = createdDialogmoterUuids
                                        )
                                    )
                                )
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
