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
import no.nav.syfo.api.dto.TildelDialogmoterDTO
import no.nav.syfo.api.endpoints.dialogmoteApiEnhetUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.api.endpoints.dialogmoteTildelPath
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

class TildelDialogmoteApiV2Test {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteRepository = externalMockEnvironment.moteRepository

    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val veilederCallerIdent = UserConstants.VEILEDER_IDENT
    private val veilederIdentTildelesMoter = UserConstants.VEILEDER_IDENT_2
    private val veilederCallerToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        veilederCallerIdent,
    )

    private val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${UserConstants.ENHET_NR.value}"
    private val urlTildelMote = "$dialogmoteApiV2Basepath$dialogmoteTildelPath"
    private val newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
    private val newDialogmoteAnnenArbeidstaker = generateNewDialogmote(UserConstants.ARBEIDSTAKER_ANNEN_FNR)

    @BeforeEach
    fun beforeEach() {
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {

        @Test
        fun `should tildele dialogmoter if request is successful`() {
            val createdDialogmoterUuids = mutableListOf<UUID>()
            database.connection.run { this.createNewDialogmoteWithReferences(newDialogmote) }
            database.connection.run { this.createNewDialogmoteWithReferences(newDialogmoteAnnenArbeidstaker) }

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )

                val response = client.get(urlMoterEnhet) {
                    bearerAuth(veilederCallerToken)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(2, dialogmoteList.size)
                assertTrue(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederCallerIdent })
                assertFalse(dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederIdentTildelesMoter })

                createdDialogmoterUuids.addAll(dialogmoteList.map { UUID.fromString(it.uuid) })

                client.patch(urlTildelMote) {
                    bearerAuth(veilederCallerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        TildelDialogmoterDTO(
                            veilederIdent = veilederIdentTildelesMoter,
                            dialogmoteUuids = createdDialogmoterUuids
                        )
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, response.status)
                }
            }

            val dialogmoter = moteRepository.getUnfinishedMoterForEnhet(EnhetNr(UserConstants.ENHET_NR.value))
            assertEquals(2, dialogmoter.size)
            assertTrue(dialogmoter.all { dialogmote -> dialogmote.tildeltVeilederIdent == veilederIdentTildelesMoter })
            assertFalse(dialogmoter.all { dialogmote -> dialogmote.tildeltVeilederIdent == veilederCallerIdent })
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {

        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.patch(urlTildelMote)
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        @Test
        fun `should return status Bad Request if no dialogmoteUuids supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.patch(urlTildelMote) {
                    bearerAuth(veilederCallerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        TildelDialogmoterDTO(
                            veilederIdent = veilederIdentTildelesMoter,
                            dialogmoteUuids = emptyList()
                        )
                    )
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if denied access to dialogmøte person`() {
            val createdDialogmoterUuids = mutableListOf<UUID>()

            val newDialogmoteNoVeilederAccess =
                generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
            database.connection.use { connection ->
                val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                    newDialogmote = newDialogmoteNoVeilederAccess
                )
                createdDialogmoterUuids.add(dialogmoteIdPair.second)
            }

            testApplication {
                val client = setupApiAndClient()
                val response = client.patch(urlTildelMote) {
                    bearerAuth(veilederCallerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        TildelDialogmoterDTO(
                            veilederIdent = veilederIdentTildelesMoter,
                            dialogmoteUuids = createdDialogmoterUuids,
                        )
                    )
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if contains dialogmøte with denied access to person`() {
            val createdDialogmoteUuid =
                mutableListOf(database.connection.run { this.createNewDialogmoteWithReferences(newDialogmote) }.dialogmoteIdPair.second)

            val newDialogmoteNoVeilederAccess =
                generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
            database.connection.use { connection ->
                val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                    newDialogmote = newDialogmoteNoVeilederAccess
                )
                createdDialogmoteUuid.add(dialogmoteIdPair.second)
            }

            testApplication {
                val client = setupApiAndClient()
                val response = client.patch(urlTildelMote) {
                    bearerAuth(veilederCallerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        TildelDialogmoterDTO(
                            veilederIdent = veilederIdentTildelesMoter,
                            dialogmoteUuids = createdDialogmoteUuid,
                        )
                    )
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
