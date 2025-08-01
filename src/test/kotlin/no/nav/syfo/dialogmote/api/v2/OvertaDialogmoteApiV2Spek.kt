package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteActionsApiOvertaPath
import no.nav.syfo.api.endpoints.dialogmoteApiEnhetUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.dto.OvertaDialogmoterDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OvertaDialogmoteApiV2Spek : Spek({
    describe(OvertaDialogmoteApiV2Spek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

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
            val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/$ENHET_NR.value"
            val urlOvertaMoter = "$dialogmoteApiV2Basepath$dialogmoteActionsApiOvertaPath"
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
            val newDialogmoteDTOAnnenArbeidstaker = generateNewDialogmoteDTO(ARBEIDSTAKER_ANNEN_FNR)

            describe("Happy path") {
                it("should overta Dialogmoter if request is successfull") {

                    testApplication {
                        val createdDialogmoterUuids = mutableListOf<String>()
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        client.postMote(validTokenV2, newDialogmoteDTO)
                        client.postMote(validTokenV2AnnenVeileder, newDialogmoteDTOAnnenArbeidstaker)

                        client.get(urlMoterEnhet) {
                            bearerAuth(validTokenV2)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = body<List<DialogmoteDTO>>()
                            dialogmoteList.size shouldBeEqualTo 2
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT } shouldBeEqualTo true
                            dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT_2 } shouldBeEqualTo true

                            createdDialogmoterUuids.addAll(dialogmoteList.map { it.uuid })
                        }

                        client.post(urlOvertaMoter) {
                            bearerAuth(validTokenV2)
                            contentType(ContentType.Application.Json)
                            setBody(OvertaDialogmoterDTO(createdDialogmoterUuids))
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        val response = client.get(urlMoterEnhet) {
                            bearerAuth(validTokenV2)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 2
                        dialogmoteList.all { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT } shouldBeEqualTo true
                    }
                }
            }

            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        val response = client.post(urlOvertaMoter)
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("should return status Bad Request if no dialogmoteUuids supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        val response = client.post(urlOvertaMoter) {
                            bearerAuth(validTokenV2)
                            contentType(ContentType.Application.Json)
                            setBody(OvertaDialogmoterDTO(emptyList()))
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
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

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        client.post(urlOvertaMoter) {
                            bearerAuth(validTokenV2)
                            contentType(ContentType.Application.Json)
                            setBody(OvertaDialogmoterDTO(createdDialogmoterUuids))
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }

                it("should return status Forbidden if contains dialogmøte with denied access to person") {

                    testApplication {
                        val createdDialogmoterUuids = mutableListOf<String>()
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        client.postMote(validTokenV2AnnenVeileder, newDialogmoteDTO)

                        client.get(urlMoterEnhet) {
                            bearerAuth(validTokenV2)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = body<List<DialogmoteDTO>>()
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
                        val response = client.post(urlOvertaMoter) {
                            bearerAuth(validTokenV2)
                            contentType(ContentType.Application.Json)
                            setBody(OvertaDialogmoterDTO(createdDialogmoterUuids))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
