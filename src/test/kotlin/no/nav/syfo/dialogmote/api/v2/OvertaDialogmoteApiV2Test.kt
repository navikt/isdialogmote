package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.dto.OvertaDialogmoterDTO
import no.nav.syfo.api.endpoints.dialogmoteActionsApiOvertaPath
import no.nav.syfo.api.endpoints.dialogmoteApiEnhetUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OvertaDialogmoteApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val validTokenV2 = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )
    private val validTokenV2AnnenVeileder = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT_2,
    )
    private val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${ENHET_NR.value}"
    private val urlOvertaMoter = "$dialogmoteApiV2Basepath$dialogmoteActionsApiOvertaPath"
    private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
    private val newDialogmoteDTOAnnenArbeidstaker = generateNewDialogmoteDTO(ARBEIDSTAKER_ANNEN_FNR)

    @BeforeEach
    fun setup() {
        database.dropData()

        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @Test
        fun `should overta Dialogmoter if request is successfull`() {
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
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(2, dialogmoteList.size)
                    assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT })
                    assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT_2 })

                    createdDialogmoterUuids.addAll(dialogmoteList.map { it.uuid })
                }

                client.post(urlOvertaMoter) {
                    bearerAuth(validTokenV2)
                    contentType(ContentType.Application.Json)
                    setBody(OvertaDialogmoterDTO(createdDialogmoterUuids))
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val response = client.get(urlMoterEnhet) {
                    bearerAuth(validTokenV2)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(2, dialogmoteList.size)
                assertTrue(dialogmoteList.all { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == UserConstants.VEILEDER_IDENT })
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
                    esyfovarselProducer = esyfovarselProducerMock
                )
                val response = client.post(urlOvertaMoter)
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        @Test
        fun `should return status Bad Request if no dialogmoteUuids supplied`() {
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
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if denied access to dialogmøte person`() {
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
                    assertEquals(HttpStatusCode.Forbidden, status)
                }
            }
        }

        @Test
        fun `should return status Forbidden if contains dialogmøte with denied access to person`() {
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
                    assertEquals(HttpStatusCode.OK, status)
                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)
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

                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
