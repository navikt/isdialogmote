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
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiEnhetUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.transaction
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GetDialogmoteEnhetApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val urlEnhetAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR.value}"
    private val urlEnhetAccessIncludeHistoriske = "$urlEnhetAccess?inkluderHistoriske=true"
    private val urlEnhetNoAccess = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR_NO_ACCESS.value}"
    private val validTokenV2 = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
        database.dropData()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

        @Test
        fun `should return DialogmoteList with unfinished dialogmote if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                client.postMote(validTokenV2, newDialogmoteDTO)

                val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_ANNEN_FNR)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmote
                    )
                }
                val newDialogmoteFerdigstilt =
                    generateNewDialogmote(ARBEIDSTAKER_FNR, status = Dialogmote.Status.FERDIGSTILT)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmoteFerdigstilt
                    )
                }
                val newDialogmoteAvlyst =
                    generateNewDialogmote(ARBEIDSTAKER_FNR, status = Dialogmote.Status.AVLYST)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmoteAvlyst
                    )
                }

                val response = client.get(urlEnhetAccess) {
                    bearerAuth(validTokenV2)
                }

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(2, dialogmoteList.size)

                val dialogmoteDTO_0 = dialogmoteList[0]
                assertEquals(ENHET_NR.value, dialogmoteDTO_0.tildeltEnhet)
                assertEquals(newDialogmote.arbeidstaker.personIdent.value, dialogmoteDTO_0.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmote.arbeidsgiver.virksomhetsnummer.value,
                    dialogmoteDTO_0.arbeidsgiver.virksomhetsnummer
                )
                assertEquals(newDialogmote.tidSted.sted, dialogmoteDTO_0.sted)
                assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO_0.status)

                val dialogmoteDTO_1 = dialogmoteList[1]
                assertEquals(ENHET_NR.value, dialogmoteDTO_1.tildeltEnhet)
                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO_1.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO_1.arbeidsgiver.virksomhetsnummer
                )
                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO_1.sted)
                assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO_1.status)
            }
        }

        @Test
        fun `should return DialogmoteList with all dialogmoter if request with inkluderHistoriske parameter is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                client.postMote(validTokenV2, newDialogmoteDTO)

                val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_ANNEN_FNR)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmote
                    )
                }
                val newDialogmoteFerdigstilt =
                    generateNewDialogmote(ARBEIDSTAKER_FNR, status = Dialogmote.Status.FERDIGSTILT)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmoteFerdigstilt
                    )
                }
                val newDialogmoteAvlyst =
                    generateNewDialogmote(ARBEIDSTAKER_FNR, status = Dialogmote.Status.AVLYST)
                database.transaction {
                    createNewDialogmoteWithReferences(
                        newDialogmote = newDialogmoteAvlyst
                    )
                }

                val response = client.get(urlEnhetAccessIncludeHistoriske) {
                    bearerAuth(validTokenV2)
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                assertEquals(4, dialogmoteList.size)
                assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == Dialogmote.Status.INNKALT.name })
                assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == Dialogmote.Status.AVLYST.name })
                assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.status == Dialogmote.Status.FERDIGSTILT.name })
            }
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {
        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                val response = client.get(urlEnhetAccess)
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if denied access to Enhet`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                val response = client.get(urlEnhetNoAccess) {
                    bearerAuth(validTokenV2)
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
