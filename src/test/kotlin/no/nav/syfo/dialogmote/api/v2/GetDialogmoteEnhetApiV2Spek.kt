package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteApiEnhetUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GetDialogmoteEnhetApiV2Spek : Spek({
    describe(GetDialogmoteEnhetApiV2Spek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        beforeEachTest {
            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
            database.dropData()
        }

        describe("Get Dialogmoter for EnhetNr") {
            val urlEnhetAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/$ENHET_NR.value"
            val urlEnhetAccessIncludeHistoriske = "$urlEnhetAccess?inkluderHistoriske=true"
            val urlEnhetNoAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/$ENHET_NR_NO_ACCESS.value"
            val validTokenV2 = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

                it("should return DialogmoteList with unfinished dialogmote if request is successful") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        client.postMote(validTokenV2, newDialogmoteDTO)

                        val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_ANNEN_FNR)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmote
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

                        val response = client.get(urlEnhetAccess) {
                            bearerAuth(validTokenV2)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 2

                        val dialogmoteDTO_0 = dialogmoteList[0]
                        dialogmoteDTO_0.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO_0.arbeidstaker.personIdent shouldBeEqualTo newDialogmote.arbeidstaker.personIdent.value
                        dialogmoteDTO_0.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmote.arbeidsgiver.virksomhetsnummer.value
                        dialogmoteDTO_0.sted shouldBeEqualTo newDialogmote.tidSted.sted
                        dialogmoteDTO_0.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                        val dialogmoteDTO_1 = dialogmoteList[1]
                        dialogmoteDTO_1.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO_1.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO_1.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO_1.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO_1.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                    }
                }

                it("should return DialogmoteList with all dialogmoter if request with inkluderHistoriske parameter is successful") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        client.postMote(validTokenV2, newDialogmoteDTO)

                        val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_ANNEN_FNR)
                        database.connection.use { connection ->
                            connection.createNewDialogmoteWithReferences(
                                newDialogmote = newDialogmote
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

                        val response = client.get(urlEnhetAccessIncludeHistoriske) {
                            bearerAuth(validTokenV2)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = response.body<List<DialogmoteDTO>>()
                        dialogmoteList.size shouldBeEqualTo 4
                        dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.INNKALT.name } shouldBeEqualTo true
                        dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.AVLYST.name } shouldBeEqualTo true
                        dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == DialogmoteStatus.FERDIGSTILT.name } shouldBeEqualTo true
                    }
                }
            }

            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        val response = client.get(urlEnhetAccess)
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("should return status Forbidden if denied access to Enhet") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        val response = client.get(urlEnhetNoAccess) {
                            bearerAuth(validTokenV2)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
