package no.nav.syfo.cronjob.journalforing

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
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.ereg.EregClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.infrastructure.database.dialogmote.PdfService
import no.nav.syfo.infrastructure.database.dialogmote.ReferatJournalpostService
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.domain.dialogmote.toJournalpostTittel
import no.nav.syfo.infrastructure.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.time.Month
import java.util.*

class DialogmoteVarselJournalforingCronjobSpek : Spek({
    describe(DialogmoteVarselJournalforingCronjobSpek::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )

        val dialogmotedeltakerVarselJournalpostService = DialogmotedeltakerVarselJournalpostService(
            database = database,
        )
        val referatJournalpostService = ReferatJournalpostService(
            database = database,
        )
        val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)
        val dokarkivClient = DokarkivClient(
            azureAdV2Client = azureAdV2Client,
            dokarkivClientId = externalMockEnvironment.environment.dokarkivClientId,
            dokarkivBaseUrl = externalMockEnvironment.environment.dokarkivUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )

        val pdlClient = PdlClient(
            azureAdV2Client = azureAdV2Client,
            pdlClientId = externalMockEnvironment.environment.pdlClientId,
            pdlUrl = externalMockEnvironment.environment.pdlUrl,
            valkeyStore = externalMockEnvironment.redisCache,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        val eregClient = EregClient(
            baseUrl = externalMockEnvironment.environment.eregUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        val pdfService = PdfService(
            database = database,
        )

        val dialogmoteVarselJournalforingCronjob = DialogmoteVarselJournalforingCronjob(
            dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
            referatJournalpostService = referatJournalpostService,
            pdfService = pdfService,
            dokarkivClient = dokarkivClient,
            pdlClient = pdlClient,
            eregClient = eregClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
        )

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

        describe("Journalfor ArbeidstakerVarsel with type Innkalling") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                UserConstants.VEILEDER_IDENT,
            )

            it("should update journalpost when ferdigstilt") {
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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 2
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 2
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidstaker(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidsgiver(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
            }
            it("should update journalpost when behandler") {
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

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"

                    client.post(urlMoteUUIDPostTidSted) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(generateEndreDialogmoteTidStedDTOWithBehandler())
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"

                    client.post(urlMoteUUIDAvlys) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(generateAvlysDialogmoteDTO())
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    database.updateMoteStatus(UUID.fromString(createdDialogmoteUUID), DialogmoteStatus.NYTT_TID_STED)

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(generateNewReferatDTO(behandlerOppgave = "Behandler skal gjøre sånn"))
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
            }

            it("should not update journalpost when behandler not mottar referat") {
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

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
            }

            it("should update journalpost when avlyst") {
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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                    val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                    client.post(urlMoteUUIDAvlys) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(avlysDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidstakerVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 3
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 3
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.referatJournalforingJobArbeidstaker(result)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
            }

            it("should fail to update journalpost if no JournalpostId is found") {
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

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }
            }
            it("should fail to update journalpost if call to ereg fails") {
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

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                runBlocking {
                    val result = DialogmoteCronjobResult()
                    dialogmoteVarselJournalforingCronjob.dialogmoteArbeidsgiverVarselJournalforingJob(result)

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }
            }
            it("Check that correct title is generated") {
                val moteTidspunkt = LocalDateTime.of(2022, Month.JUNE, 20, 0, 0, 0)
                val referatUtenEndring = createReferat(
                    begrunnelseEndring = null,
                    updatedAt = LocalDateTime.now(),
                )

                val tittelUtenEndring = referatUtenEndring.toJournalpostTittel(moteTidspunkt)
                tittelUtenEndring shouldBeEqualTo "Referat fra dialogmøte 20. juni 2022"

                val oppdatertTidspunkt = LocalDateTime.of(2022, Month.JULY, 1, 0, 0, 0)
                val referatMedEndring = createReferat(
                    begrunnelseEndring = "Dette er en begrunnelse",
                    updatedAt = oppdatertTidspunkt,
                )
                val tittelMedEndring = referatMedEndring.toJournalpostTittel(moteTidspunkt)
                tittelMedEndring shouldBeEqualTo "Referat fra dialogmøte 20. juni 2022 - Endret 1. juli 2022"
            }
        }
    }
})

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
