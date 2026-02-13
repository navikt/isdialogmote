package no.nav.syfo.cronjob.journalforing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteApiMoteAvlysPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.application.ReferatJournalpostService
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.domain.dialogmote.toJournalpostTittel
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrukerIdType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.ereg.EregClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import no.nav.syfo.infrastructure.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.Month
import java.util.*

class DialogmoteVarselJournalforingCronjobTest {

    private lateinit var dokarkivClient: DokarkivClient
    private lateinit var dialogmoteVarselJournalforingCronjob: DialogmoteVarselJournalforingCronjob

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

    private val dialogmotedeltakerVarselJournalpostService = DialogmotedeltakerVarselJournalpostService(
        database = database,
    )
    private val referatJournalpostService = ReferatJournalpostService(
        database = database,
        moteRepository = externalMockEnvironment.moteRepository,
    )
    private val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)

    private val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = externalMockEnvironment.environment.pdlClientId,
        pdlUrl = externalMockEnvironment.environment.pdlUrl,
        valkeyStore = externalMockEnvironment.redisCache,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val eregClient = EregClient(
        baseUrl = externalMockEnvironment.environment.eregUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val dialogmeldingClient = DialogmeldingClient(
        azureAdClient = azureAdV2Client,
        clientId = externalMockEnvironment.environment.dialogmeldingClientId,
        url = externalMockEnvironment.environment.dialogmeldingUrl,
        client = externalMockEnvironment.mockHttpClient,
    )

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun beforeEach() {
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse

        dokarkivClient = DokarkivClient(
            azureAdV2Client = azureAdV2Client,
            dokarkivClientId = externalMockEnvironment.environment.dokarkivClientId,
            dokarkivBaseUrl = externalMockEnvironment.environment.dokarkivUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
            dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
            referatJournalpostService = referatJournalpostService,
            dokarkivClient = dokarkivClient,
            pdlClient = pdlClient,
            eregClient = eregClient,
            dialogmeldingClient = dialogmeldingClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
            pdfRepository = externalMockEnvironment.pdfRepository,
        )
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `should update journalpost when ferdigstilt`() {
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
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(2, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(2, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidstaker(result)

            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidsgiver(result)

            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `should update journalpost when behandler`() {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
        val journalpostRequestSlot = slot<JournalpostRequest>()

        dokarkivClient = mockk<DokarkivClient>(relaxed = true)

        dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
            dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
            referatJournalpostService = referatJournalpostService,
            dokarkivClient = dokarkivClient,
            pdlClient = pdlClient,
            eregClient = eregClient,
            dialogmeldingClient = dialogmeldingClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
            pdfRepository = externalMockEnvironment.pdfRepository,
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            runBlocking {
                val result = DialogmoteCronjobResult()
                dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)
                coVerify(exactly = 1) {
                    dokarkivClient.journalfor(
                        capture(
                            journalpostRequestSlot
                        )
                    )
                }

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)

                assertEquals(BrukerIdType.HPRNR.value, journalpostRequestSlot.captured.avsenderMottaker.idType)
                assertEquals(
                    "000${UserConstants.BEHANDLER_HPRID}",
                    journalpostRequestSlot.captured.avsenderMottaker.id!!
                )
            }

            val urlMoteUUIDPostTidSted =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"

            client.post(urlMoteUUIDPostTidSted) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(generateEndreDialogmoteTidStedDTOWithBehandler())
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            runBlocking {
                val result = DialogmoteCronjobResult()
                dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

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
                val result = DialogmoteCronjobResult()
                dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            database.updateMoteStatus(UUID.fromString(createdDialogmoteUUID), Dialogmote.Status.NYTT_TID_STED)

            val urlMoteUUIDFerdigstill =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

            client.post(urlMoteUUIDFerdigstill) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(generateNewReferatDTO(behandlerOppgave = "Behandler skal gjøre sånn"))
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

        runBlocking {
            clearMocks(dokarkivClient)

            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)
            coVerify(exactly = 1) {
                dokarkivClient.journalfor(
                    capture(
                        journalpostRequestSlot
                    )
                )
            }

            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
            assertEquals(BrukerIdType.HPRNR.value, journalpostRequestSlot.captured.avsenderMottaker.idType)
            assertEquals("000${UserConstants.BEHANDLER_HPRID}", journalpostRequestSlot.captured.avsenderMottaker.id!!)
        }
    }

    @Test
    fun `should not update journalpost when behandler not mottar referat`() {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

            runBlocking {
                val result = DialogmoteCronjobResult()
                dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            val urlMoteUUIDFerdigstill =
                "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
            val ferdigstillDialogMoteDto = generateNewReferatDTO(
                behandlerOppgave = "Behandler skal gjøre sånn",
                behandlerMottarReferat = false,
            )
            client.post(urlMoteUUIDFerdigstill) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(ferdigstillDialogMoteDto)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `should update journalpost when avlyst`() {
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
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(3, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(3, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidstaker(result)

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `should fail to update journalpost if no JournalpostId is found`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_NO_JOURNALFORING)

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            client.postMote(validToken, newDialogmoteDTO)
        }

        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

            assertEquals(1, result.failed)
            assertEquals(0, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

            assertEquals(1, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `should fail to update journalpost if call to ereg fails`() {
        val newDialogmoteDTO = generateNewDialogmoteDTO(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER_EREG_FAILS.value,
        )

        testApplication {
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )
            client.postMote(validToken, newDialogmoteDTO)
        }

        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        runBlocking {
            val result = DialogmoteCronjobResult()
            dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

            assertEquals(1, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `Check that correct title is generated`() {
        val moteTidspunkt = LocalDateTime.of(2022, Month.JUNE, 20, 0, 0, 0)
        val referatUtenEndring = createReferat(
            begrunnelseEndring = null,
            updatedAt = LocalDateTime.now(),
        )

        val tittelUtenEndring = referatUtenEndring.toJournalpostTittel(moteTidspunkt)
        assertEquals("Referat fra dialogmøte 20. juni 2022", tittelUtenEndring)

        val oppdatertTidspunkt = LocalDateTime.of(2022, Month.JULY, 1, 0, 0, 0)
        val referatMedEndring = createReferat(
            begrunnelseEndring = "Dette er en begrunnelse",
            updatedAt = oppdatertTidspunkt,
        )
        val tittelMedEndring = referatMedEndring.toJournalpostTittel(moteTidspunkt)
        assertEquals("Referat fra dialogmøte 20. juni 2022 - Endret 1. juli 2022", tittelMedEndring)
    }
}

fun createReferat(
    begrunnelseEndring: String?,
    updatedAt: LocalDateTime,
) = Referat(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = updatedAt,
    moteId = 1,
    motedeltakerArbeidstakerId = 1,
    motedeltakerArbeidsgiverId = 1,
    digitalt = true,
    situasjon = "",
    konklusjon = "",
    arbeidstakerOppgave = "",
    arbeidsgiverOppgave = "",
    veilederOppgave = null,
    behandlerOppgave = null,
    narmesteLederNavn = "",
    document = emptyList(),
    pdfId = null,
    journalpostIdArbeidstaker = null,
    lestDatoArbeidstaker = null,
    lestDatoArbeidsgiver = null,
    andreDeltakere = emptyList(),
    brevBestillingsId = null,
    brevBestiltTidspunkt = null,
    ferdigstilt = true,
    begrunnelseEndring = begrunnelseEndring,
)
