package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.api.endpoints.dialogmoteApiVeilederIdentUrlPath
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

class GetDialogmoteApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val esyfovarselHendelse = generateInkallingHendelse()
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
    private val urlMoteVeilederIdent = "$dialogmoteApiV2Basepath$dialogmoteApiVeilederIdentUrlPath"
    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @BeforeEach
        fun setup() {
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
        }

        @AfterEach
        fun teardown() {
            database.dropData()
        }

        @Test
        fun `should return DialogmoteList if request is successful`() {
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

                assertEquals(HttpStatusCode.OK, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                assertEquals("https://meet.google.com/xyz", dialogmoteDTO.videoLink)
            }
        }

        @Test
        fun `should return DialogmoteList based on VeilederIdent`() {
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

                assertEquals(HttpStatusCode.OK, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(2, dialogmoteList.size)
            }
        }

        @Test
        fun `should return DialogmoteList if request is successful optional values missing`() {
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

                assertEquals(HttpStatusCode.OK, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)
                assertEquals("", dialogmoteDTO.videoLink)
            }
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {
        @BeforeEach
        fun setup() {
            clearMocks(esyfovarselProducerMock)
        }

        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(urlMote)

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            }
        }

        @Test
        fun `veilederident should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(urlMoteVeilederIdent)

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            }
        }

        @Test
        fun `should return empty dialogmoteList if no access`() {
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

                assertEquals(HttpStatusCode.OK, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                assertEquals(0, dialogmoteList.size)
            }
        }

        @Test
        fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(urlMote) {
                    bearerAuth(validToken)
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            }
        }

        @Test
        fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(urlMote) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            }
        }

        @Test
        fun `should return status Forbidden if denied access to person`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(urlMote) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_VEILEDER_NO_ACCESS.value)
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            }
        }
    }
}
