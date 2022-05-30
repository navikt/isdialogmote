package no.nav.syfo.cronjob.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.time.Month
import java.util.*

class DialogmoteVarselJournalforingCronjobSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(DialogmoteVarselJournalforingCronjobSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val dineSykmeldteVarselProducer = mockk<DineSykmeldteVarselProducer>()
            justRun { dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
                mqSenderMock = mqSenderMock,
                altinnMock = altinnMock,
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
                dokarkivBaseUrl = externalMockEnvironment.dokarkivMock.url,
            )

            val pdlClient = PdlClient(
                azureAdV2Client = azureAdV2Client,
                pdlClientId = externalMockEnvironment.environment.pdlClientId,
                pdlUrl = externalMockEnvironment.pdlMock.url,
            )
            val eregClient = EregClient(
                azureAdClient = azureAdV2Client,
                isproxyClientId = externalMockEnvironment.environment.isproxyClientId,
                baseUrl = externalMockEnvironment.environment.isproxyUrl,
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
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                it("should update journalpost when ferdigstilt") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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
                    val createdDialogmoteUUID: String
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                        dialogmoteList.size shouldBeEqualTo 1
                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        dialogmoteDTO.behandler.shouldNotBeNull()
                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTOWithBehandler()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteVarselJournalforingCronjob.dialogmoteBehandlerVarselJournalforingJob(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                    val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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
                    val ferdigstillDialogMoteDto = generateNewReferatDTO(behandlerOppgave = "Behandler skal gjøre sånn")

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(ferdigstillDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    runBlocking {
                        val result = DialogmoteCronjobResult()
                        dialogmoteVarselJournalforingCronjob.referatJournalforingJobBehandler(result)

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                }

                it("should update journalpost when avlyst") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
                    val createdDialogmoteUUID: String

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                        createdDialogmoteUUID = dialogmoteDTO.uuid
                    }

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()
                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDAvlys =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                    val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                    with(
                        handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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

                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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
