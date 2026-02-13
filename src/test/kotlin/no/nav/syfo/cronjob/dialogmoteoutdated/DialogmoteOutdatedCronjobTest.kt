package no.nav.syfo.cronjob.dialogmoteoutdated

import io.ktor.client.call.*
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
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteAvlysPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import no.nav.syfo.infrastructure.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.application.DialogmotedeltakerService
import no.nav.syfo.application.DialogmoterelasjonService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateAvlysDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateEndreDialogmoteTidStedDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DialogmoteOutdatedCronjobTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )
    private val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)
    private val tokendingsClient = mockk<TokendingsClient>(relaxed = true)
    private val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleClientId = externalMockEnvironment.environment.isoppfolgingstilfelleClientId,
        isoppfolgingstilfelleBaseUrl = externalMockEnvironment.environment.isoppfolgingstilfelleUrl,
        cache = mockk<ValkeyStore>(relaxed = true),
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        moteStatusEndretRepository = MoteStatusEndretRepository(database),
    )
    private val arbeidstakerVarselService = ArbeidstakerVarselService(
        esyfovarselProducer = esyfovarselProducerMock,
    )
    private val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
        moteRepository = externalMockEnvironment.moteRepository
    )
    private val dialogmoterelasjonService = DialogmoterelasjonService(
        dialogmotedeltakerService = dialogmotedeltakerService,
        database = database,
    )
    private val dialogmoteOutdatedCronjob = DialogmoteOutdatedCronjob(
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        database = database,
        outdatedDialogmoterCutoffMonths = 1,
    )
    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun beforeEach() {
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

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
    fun `Setter status paa gammel innkalling til LUKKET`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().minusDays(40),
        )
        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            client.postMote(validToken, newDialogmoteDTO)

            runBlocking {
                val result = dialogmoteOutdatedCronjob.dialogmoteOutdatedJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)
            assertEquals(HttpStatusCode.OK, response.status)

            val dialogmoteList = response.body<List<DialogmoteDTO>>()

            assertEquals(1, dialogmoteList.size)

            val dialogmoteDTO = dialogmoteList.first()
            assertEquals(Dialogmote.Status.LUKKET.name, dialogmoteDTO.status)
        }
    }

    @Test
    fun `Setter status paa gammel innkalling med status NYTT_TID_STED til LUKKET`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().minusDays(40),
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            val urlMoteUUIDPostTidSted =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
            val newTidStedDTO = generateEndreDialogmoteTidStedDTO(
                tid = LocalDateTime.now().minusDays(39),
            )

            client.post(urlMoteUUIDPostTidSted) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(newTidStedDTO)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            runBlocking {
                val result = dialogmoteOutdatedCronjob.dialogmoteOutdatedJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)
            assertEquals(HttpStatusCode.OK, response.status)

            val dialogmoteList = response.body<List<DialogmoteDTO>>()
            assertEquals(1, dialogmoteList.size)

            val dialogmoteDTO = dialogmoteList.first()
            assertEquals(Dialogmote.Status.LUKKET.name, dialogmoteDTO.status)
        }
    }

    @Test
    fun `Endrer ikke status paa ny innkalling`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().minusDays(20),
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            client.postMote(validToken, newDialogmoteDTO)

            runBlocking {
                val result = dialogmoteOutdatedCronjob.dialogmoteOutdatedJob()

                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }

            val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)
            assertEquals(HttpStatusCode.OK, response.status)

            val dialogmoteList = response.body<List<DialogmoteDTO>>()
            assertEquals(1, dialogmoteList.size)

            val dialogmoteDTO = dialogmoteList.first()
            assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)
        }
    }

    @Test
    fun `Setter ikke status paa gammelt mote med status FERDIGSTILT til LUKKET`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().minusDays(40),
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )

            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            val urlMoteUUIDFerdigstill =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
            client.post(urlMoteUUIDFerdigstill) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(generateNewReferatDTO())
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            runBlocking {
                val result = dialogmoteOutdatedCronjob.dialogmoteOutdatedJob()

                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }

            val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)
            assertEquals(HttpStatusCode.OK, response.status)

            val dialogmoteList = response.body<List<DialogmoteDTO>>()
            assertEquals(1, dialogmoteList.size)

            val dialogmoteDTO = dialogmoteList.first()
            assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
        }
    }

    @Test
    fun `Setter ikke status paa gammelt mote med status AVLYST til LUKKET`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            dato = LocalDateTime.now().minusDays(40),
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            val urlMoteUUIDAvlys =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
            client.post(urlMoteUUIDAvlys) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(generateAvlysDialogmoteDTO())
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            runBlocking {
                val result = dialogmoteOutdatedCronjob.dialogmoteOutdatedJob()

                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }

            val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)
            assertEquals(HttpStatusCode.OK, response.status)

            val dialogmoteList = response.body<List<DialogmoteDTO>>()
            assertEquals(1, dialogmoteList.size)

            val dialogmoteDTO = dialogmoteList.first()
            assertEquals(Dialogmote.Status.AVLYST.name, dialogmoteDTO.status)
        }
    }
}
