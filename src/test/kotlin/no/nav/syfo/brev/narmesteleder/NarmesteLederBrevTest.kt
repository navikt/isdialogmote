package no.nav.syfo.brev.narmesteleder

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.*
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.domain.ArbeidstakerResponsDTO
import no.nav.syfo.domain.BrevType
import no.nav.syfo.domain.NarmesteLederBrevDTO
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.application.DialogmotedeltakerService
import no.nav.syfo.application.DialogmoterelasjonService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.infrastructure.database.getDialogmote
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR_2
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.pdfInnkalling
import no.nav.syfo.testhelper.mock.pdfReferat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime
import java.util.*

class NarmesteLederBrevTest {

    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val esyfovarselProducer = mockk<EsyfovarselProducer>()
    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val arbeidstakerVarselService = ArbeidstakerVarselService(
        esyfovarselProducer = esyfovarselProducer,
    )
    private val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = externalMockEnvironment.oppfolgingstilfelleClient,
        moteStatusEndretRepository = MoteStatusEndretRepository(database),
    )
    private val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
        moteRepository = externalMockEnvironment.moteRepository,
    )
    private val dialogmoterelasjonService = DialogmoterelasjonService(
        moteRepository = externalMockEnvironment.moteRepository,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
        // Add dummy deltakere so that id for deltaker and mote does not match by accident
        database.addDummyDeltakere()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        private val newDialogmoteDTOOther = generateNewDialogmoteDTO(
            ARBEIDSTAKER_FNR,
            virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
        )
        private val validTokenSelvbetjening = generateJWTTokenx(
            audience = externalMockEnvironment.environment.tokenxClientId,
            issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            pid = NARMESTELEDER_FNR.value,
        )
        private val validTokenVeileder = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )
        private val incorrectTokenSelvbetjening = generateJWTTokenx(
            audience = externalMockEnvironment.environment.tokenxClientId,
            issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            pid = NARMESTELEDER_FNR_2.value,
        )

        @Test
        fun `Should return OK when les and response`() {
            testApplication {
                var uuid: String
                var createdDialogmoteUUID: String
                var createdDialogmoteDeltakerArbeidsgiverUUID: String
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validTokenVeileder, newDialogmoteDTO)
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                clearMocks(esyfovarselProducerMock)

                client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dto = body<List<DialogmoteDTO>>().first()
                    assertEquals(Dialogmote.Status.INNKALT.name, dto.status)
                    createdDialogmoteUUID = dto.uuid
                    createdDialogmoteDeltakerArbeidsgiverUUID = dto.arbeidsgiver.uuid
                }

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    contentType(ContentType.Application.Json)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(1, nlBrevList.size)

                    val narmesteLederBrevDTO = nlBrevList.first()
                    assertNotNull(narmesteLederBrevDTO)
                    assertNull(narmesteLederBrevDTO.lestDato)

                    uuid = narmesteLederBrevDTO.uuid
                }

                val urlNarmesteLederBrevUUIDLes =
                    "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiLesPath"

                client.post(urlNarmesteLederBrevUUIDLes) {
                    bearerAuth(incorrectTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }

                client.post(urlNarmesteLederBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(1, nlBrevList.size)

                    val narmesteLederBrevDTO = nlBrevList.first()
                    assertNotNull(narmesteLederBrevDTO)
                    assertNotNull(narmesteLederBrevDTO.lestDato)
                }

                val urlNarmesteLederBrevUUIDRespons =
                    "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiResponsPath"

                client.post(urlNarmesteLederBrevUUIDRespons) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ArbeidstakerResponsDTO(
                            svarType = DialogmoteSvarType.KOMMER.name,
                            svarTekst = "Det passer bra - det/også <code>",
                        )
                    )
                }.apply {
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateKommerSvarHendelse()
                        )
                    }
                    clearMocks(esyfovarselProducerMock)
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val narmesteLederBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(1, narmesteLederBrevList.size)

                    val narmesteLederBrevDTO = narmesteLederBrevList.firstOrNull()
                    assertNotNull(narmesteLederBrevDTO)
                    assertEquals(DialogmoteSvarType.KOMMER.name, narmesteLederBrevDTO!!.svar!!.svarType)
                }

                client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(
                        newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                    )
                    assertEquals(
                        DialogmoteSvarType.KOMMER.name,
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarType
                    )
                    assertEquals(
                        "Det passer bra - det/også _code_",
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarTekst
                    )
                }

                // Repeated invocation should fail
                client.post(urlNarmesteLederBrevUUIDRespons) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ArbeidstakerResponsDTO(
                            svarType = DialogmoteSvarType.KOMMER.name,
                            svarTekst = "Det passer bra det fortsatt",
                        )
                    )
                }.apply {
                    assertEquals(HttpStatusCode.BadRequest, status)
                }

                val pdfUrl = "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiPdfPath"
                client.get(pdfUrl) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfInnkalling, pdfContent)
                }

                val urlMoteUUIDReferat =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                client.post(urlMoteUUIDReferat) {
                    bearerAuth(validTokenVeileder)
                    contentType(ContentType.Application.Json)
                    setBody(generateNewReferatDTO())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                var createdReferatArbeidsgiverBrevUUID: String

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(2, arbeidsgiverBrevList.size)

                    val arbeidsgiverBrevDTO = arbeidsgiverBrevList.first()
                    assertNotNull(arbeidsgiverBrevDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidsgiverBrevDTO.brevType)
                    assertNull(arbeidsgiverBrevDTO.lestDato)
                    assertEquals(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer, arbeidsgiverBrevDTO.virksomhetsnummer)
                    assertEquals(createdDialogmoteDeltakerArbeidsgiverUUID, arbeidsgiverBrevDTO.deltakerUuid)
                    assertEquals(newDialogmoteDTO.tidSted.sted, arbeidsgiverBrevDTO.sted)
                    val isCorrectDialogmotetid =
                        LocalDateTime.now().plusDays(29).isBefore(arbeidsgiverBrevDTO.tid)
                    assertTrue(isCorrectDialogmotetid)
                    createdReferatArbeidsgiverBrevUUID = arbeidsgiverBrevDTO.uuid
                }

                val urlReferatUUIDLes =
                    "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiLesPath"
                client.post(urlReferatUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val arbeidsgiverBrevDTO: NarmesteLederBrevDTO?
                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(2, arbeidsgiverBrevList.size)

                    arbeidsgiverBrevDTO = arbeidsgiverBrevList.firstOrNull()
                    assertNotNull(arbeidsgiverBrevDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidsgiverBrevDTO!!.brevType)
                    assertNotNull(arbeidsgiverBrevDTO.lestDato)
                }

                client.post(urlReferatUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(2, arbeidsgiverBrevList.size)

                    val arbeidstakerBrevUpdatedDTO = arbeidsgiverBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevUpdatedDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidstakerBrevUpdatedDTO!!.brevType)
                    assertEquals(arbeidsgiverBrevDTO!!.lestDato, arbeidstakerBrevUpdatedDTO.lestDato)
                }

                val urlPdfForReferatNedlasting =
                    "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiPdfPath"
                client.get(urlPdfForReferatNedlasting) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfReferat, pdfContent)
                }

                val urlMoteUUIDEndreReferat =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteEndreFerdigstiltPath"
                val referatEndretDto = generateNewReferatDTO(
                    behandlerOppgave = "Dette er en en endring",
                    begrunnelseEndring = "Dette er en begrunnelse",
                )
                client.post(urlMoteUUIDEndreReferat) {
                    bearerAuth(validTokenVeileder)
                    contentType(ContentType.Application.Json)
                    setBody(referatEndretDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(3, arbeidsgiverBrevList.size)

                    val endretReferatBrevDTO = arbeidsgiverBrevList.firstOrNull()
                    assertNotNull(endretReferatBrevDTO)
                    assertEquals(BrevType.REFERAT_ENDRET.name, endretReferatBrevDTO!!.brevType)
                }
            }
        }

        @Test
        fun `Same narmesteleder and arbeidstaker, different virksomhet`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validTokenVeileder, newDialogmoteDTO)
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(
                        generateInkallingHendelse()
                    )
                }
                clearMocks(esyfovarselProducerMock)

                client.postMote(validTokenVeileder, newDialogmoteDTOOther)
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(
                        generateInkallingHendelseOtherVirksomhet()
                    )
                }
                clearMocks(esyfovarselProducerMock)

                client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    contentType(ContentType.Application.Json)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                    assertEquals(2, nlBrevList.size)

                    val narmesteLederBrevDTO = nlBrevList.first()
                    assertNotNull(narmesteLederBrevDTO)
                    assertNull(narmesteLederBrevDTO.lestDato)
                }
            }
        }

        @Test
        fun `Return OK and empty brevlist when no brev exists`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    contentType(ContentType.Application.Json)
                }

                assertEquals(HttpStatusCode.OK, response.status)

                val nlBrevList = response.body<List<NarmesteLederBrevDTO>>()
                assertEquals(0, nlBrevList.size)
            }
        }

        @Test
        fun `Return OK and empty brevlist when only brev from lukket mote exists`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val createdDialogmoteUUID: String =
                    client.postAndGetDialogmote(validTokenVeileder, newDialogmoteDTO).uuid

                val pMote = database.getDialogmote(UUID.fromString(createdDialogmoteUUID)).first()
                val mote = dialogmoterelasjonService.extendDialogmoteRelations(pMote)

                runBlocking {
                    database.connection.use { connection ->
                        dialogmotestatusService.updateMoteStatus(
                            connection = connection,
                            dialogmote = mote,
                            newDialogmoteStatus = Dialogmote.Status.LUKKET,
                            opprettetAv = "system",
                        )
                        connection.commit()
                    }
                }

                val response = client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    contentType(ContentType.Application.Json)
                }

                assertEquals(HttpStatusCode.OK, response.status)

                val nlBrevList = response.body<List<NarmesteLederBrevDTO>>()
                assertEquals(0, nlBrevList.size)
            }
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {

        @Test
        fun `Return BAD REQUEST when NAV_PERSONIDENT_HEADER is missing`() {
            val validTokenSelvbetjening = generateJWTTokenx(
                audience = externalMockEnvironment.environment.tokenxClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                pid = NARMESTELEDER_FNR.value,
            )
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )

                val response = client.get(narmesteLederBrevApiBasePath) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }
    }
}
