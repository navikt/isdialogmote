package no.nav.syfo.cronjob.statusendring

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteApiMoteAvlysPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.infrastructure.cronjob.statusendring.DialogmoteStatusEndringProducer
import no.nav.syfo.infrastructure.cronjob.statusendring.PublishDialogmoteStatusEndringCronjob
import no.nav.syfo.infrastructure.cronjob.statusendring.PublishDialogmoteStatusEndringService
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PublishDialogmoteStatusEndringCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)
    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
    private val dialogmoteStatusEndringProducer = mockk<DialogmoteStatusEndringProducer>()
    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )
    private val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
        database = database,
        dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
        moteStatusEndretRepository = moteStatusEndretRepository,
    )
    private val publishDialogmoteStatusEndringCronjob = PublishDialogmoteStatusEndringCronjob(
        publishDialogmoteStatusEndringService = publishDialogmoteStatusEndringService,
    )
    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun beforeEach() {
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        justRun { dialogmoteStatusEndringProducer.sendDialogmoteStatusEndring(any()) }

        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `should update publishedAt (ferdigstilt) without Behandler`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            val urlMoteUUIDPostTidSted =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
            val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

            client.post(urlMoteUUIDPostTidSted) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(newDialogmoteTidSted)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            val urlMoteUUIDFerdigstill =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
            val ferdigstillDialogMoteDto = generateNewReferatDTO()

            client.post(urlMoteUUIDFerdigstill) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(ferdigstillDialogMoteDto)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

        runBlocking {
            val result = publishDialogmoteStatusEndringCronjob.dialogmoteStatusEndringPublishJob()

            assertEquals(0, result.failed)
            assertEquals(3, result.updated)
        }
    }

    @Test
    fun `should update publishedAt (avlyst) with Behandler`() {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            val urlMoteUUIDPostTidSted =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
            val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTOWithBehandler()

            client.post(urlMoteUUIDPostTidSted) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(newDialogmoteTidSted)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            val urlMoteUUIDAvlys =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
            val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

            client.post(urlMoteUUIDAvlys) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(avlysDialogMoteDto)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

        runBlocking {
            val dialogmoteStatusEndretList =
                publishDialogmoteStatusEndringService.getDialogmoteStatuEndretToPublishList()
            assertEquals(3, dialogmoteStatusEndretList.size)

            val dialogmoteStatusEndretListWithBehandler =
                dialogmoteStatusEndretList.filter { dialogmoteStatusEndret ->
                    dialogmoteStatusEndret.motedeltakerBehandler
                }
            assertEquals(3, dialogmoteStatusEndretListWithBehandler.size)

            val result = publishDialogmoteStatusEndringCronjob.dialogmoteStatusEndringPublishJob()

            assertEquals(0, result.failed)
            assertEquals(3, result.updated)
        }
    }
}
