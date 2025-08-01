package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.api.endpoints.dialogmoteApiVeilederIdentUrlPath
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GetDialogmoteApiV2Spek : Spek({
    describe("DialogmoteApiSpek") {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselHendelse = generateInkallingHendelse()
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        describe("Get Dialogmoter for PersonIdent") {
            val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
            val urlMoteVeilederIdent = "$dialogmoteApiV2Basepath$dialogmoteApiVeilederIdentUrlPath"
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            describe("Happy path") {
                beforeEachTest {
                    justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                }

                beforeEachTest {
                    val altinnResponse = ReceiptExternal()
                    altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                    clearMocks(altinnMock)
                    every {
                        altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                    } returns altinnResponse
                }

                afterEachTest {
                    database.dropData()
                }

                it("should return DialogmoteList if request is successful") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)

                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"
                    }
                }

                it("should return DialogmoteList based on VeilederIdent") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)

                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, generateNewDialogmoteDTO(ARBEIDSTAKER_ANNEN_FNR))

                        verify(exactly = 2) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)

                        val response = client.get(urlMoteVeilederIdent) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 2
                    }
                }

                it("should return DialogmoteList if request is successful: optional values missing") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)

                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo ""
                    }
                }
            }

            describe("Unhappy paths") {
                beforeEachTest {
                    clearMocks(esyfovarselProducerMock)
                }

                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMote)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }
                }

                it("veilederident should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMoteVeilederIdent)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }
                }

                it("should return empty dialogmoteList if no access") {
                    val newDialogmoteVeilederNoAccess = generateNewDialogmote(ARBEIDSTAKER_VEILEDER_NO_ACCESS).copy(
                        opprettetAv = VEILEDER_IDENT,
                        tildeltVeilederIdent = VEILEDER_IDENT
                    )

                    database.connection.use { connection ->
                        connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmoteVeilederNoAccess
                        )
                    }

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMoteVeilederIdent) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()
                        dialogmoteList.size shouldBeEqualTo 0
                    }
                }

                it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMote) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }
                }

                it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMote) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }
                }

                it("should return status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.get(urlMote) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_VEILEDER_NO_ACCESS.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    }
                }
            }
        }
    }
})
