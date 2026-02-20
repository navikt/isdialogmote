package no.nav.syfo.brev.arbeidstaker

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
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.*
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.DialogmotedeltakerService
import no.nav.syfo.application.DialogmoterelasjonService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.domain.ArbeidstakerBrevDTO
import no.nav.syfo.domain.ArbeidstakerResponsDTO
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.database.getDialogmote
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.generator.generateAvlysDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.testhelper.mock.pdfInnkalling
import no.nav.syfo.testhelper.mock.pdfReferat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.time.LocalDateTime
import java.util.*

class ArbeidstakerBrevApiTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
    private val esyfovarselHendelse = mockk<ArbeidstakerHendelse>(relaxed = true)

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>(relaxed = true)

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
        dialogmotedeltakerService = dialogmotedeltakerService,
        database = database,
        moteRepository = externalMockEnvironment.moteRepository,
    )

    private val validTokenSelvbetjening = generateJWTTokenx(
        audience = externalMockEnvironment.environment.tokenxClientId,
        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
        pid = ARBEIDSTAKER_FNR.value,
    )
    private val validTokenVeileder = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        database.dropData()
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        clearMocks(altinnMock, esyfovarselProducer, esyfovarselHendelse)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse

        justRun { esyfovarselProducer.sendVarselToEsyfovarsel(any()) }
        justRun { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        // Add dummy deltakere so that id for deltaker and mote does not match by accident
        database.addDummyDeltakere()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        private val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdArbeidstakerBrevUUID: String
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                client.postMote(validTokenVeileder, newDialogmoteDTO)
                verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                clearMocks(esyfovarselProducer)

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNull(arbeidstakerBrevDTO.lestDato)

                    createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlArbeidstakerBrevUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                client.post(urlArbeidstakerBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNotNull(arbeidstakerBrevDTO.lestDato)
                    assertNull(arbeidstakerBrevDTO.svar)
                    assertEquals(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer, arbeidstakerBrevDTO.virksomhetsnummer)
                    assertEquals(newDialogmoteDTO.tidSted.sted, arbeidstakerBrevDTO.sted)
                    val isTodayBeforeDialogmotetid =
                        LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                    assertTrue(isTodayBeforeDialogmotetid)

                    clearMocks(esyfovarselProducer)
                }

                client.post(urlArbeidstakerBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.first()
                    assertEquals(arbeidstakerBrevDTO!!.lestDato, arbeidstakerBrevUpdatedDTO.lestDato)
                }

                val urlArbeidstakerBrevUUIDRespons =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"
                client.post(urlArbeidstakerBrevUUIDRespons) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ArbeidstakerResponsDTO(
                            svarType = DialogmoteSvarType.KOMMER.name,
                            svarTekst = "Det passer bra();<code>, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?.",
                        )
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(DialogmoteSvarType.KOMMER.name, arbeidstakerBrevDTO.svar!!.svarType)
                }

                client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                    assertEquals(
                        newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                    )
                    assertEquals(
                        DialogmoteSvarType.KOMMER.name,
                        dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarType
                    )
                    assertEquals(
                        "Det passer bra();_code_, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?.",
                        dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarTekst
                    )
                }

                // Repeated invocation should fail
                val response = client.post(urlArbeidstakerBrevUUIDRespons) {
                    bearerAuth(validTokenSelvbetjening)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ArbeidstakerResponsDTO(
                            svarType = DialogmoteSvarType.KOMMER.name,
                            svarTekst = "Det passer bra det fortsatt",
                        )
                    )
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }
    }

    @Nested
    @DisplayName("Happy path for arbeidstaker som har byttet fnr")
    inner class HappyPathForArbeidstakerSomHarByttetFnr {
        private val validTokenSelvbetjeningOldFnr = generateJWTTokenx(
            audience = externalMockEnvironment.environment.tokenxClientId,
            issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            pid = ARBEIDSTAKER_TREDJE_FNR.value,
        )
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FJERDE_FNR)
        private val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdArbeidstakerBrevUUID: String
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                client.postMote(validTokenVeileder, newDialogmoteDTO)

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNull(arbeidstakerBrevDTO.lestDato)

                    createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlArbeidstakerBrevUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                client.post(urlArbeidstakerBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                    assertEquals(1, arbeidstakerBrevList.size)

                    arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNotNull(arbeidstakerBrevDTO.lestDato)
                    assertNull(arbeidstakerBrevDTO.svar)
                    assertEquals(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer, arbeidstakerBrevDTO.virksomhetsnummer)
                    assertEquals(newDialogmoteDTO.tidSted.sted, arbeidstakerBrevDTO.sted)

                    clearMocks(esyfovarselProducer)
                }

                client.post(urlArbeidstakerBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevUpdatedDTO)
                    assertEquals(arbeidstakerBrevDTO!!.lestDato, arbeidstakerBrevUpdatedDTO.lestDato)
                }

                val urlArbeidstakerBrevUUIDRespons =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"
                client.post(urlArbeidstakerBrevUUIDRespons) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ArbeidstakerResponsDTO(
                            svarType = DialogmoteSvarType.KOMMER.name,
                            svarTekst = "Det passer bra",
                        )
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(urlArbeidstakerMoterList) {
                    bearerAuth(validTokenSelvbetjeningOldFnr)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(1, arbeidstakerBrevList.size)

                    arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(DialogmoteSvarType.KOMMER.name, arbeidstakerBrevDTO!!.svar!!.svarType)
                }

                val response = client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FJERDE_FNR)
                assertEquals(HttpStatusCode.OK, response.status)
                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )
                assertEquals(DialogmoteSvarType.KOMMER.name, dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarType)
                assertEquals("Det passer bra", dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarTekst)
            }
        }
    }

    @Nested
    @DisplayName("Happy path med mer enn et møte for aktuell person")
    inner class HappyPathMedMerEnnEtMoteForAktuellPerson {
        private val newDialogmoteLukket =
            generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 0", LocalDateTime.now().plusDays(5))
        private val newDialogmoteAvlyst1 =
            generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 1", LocalDateTime.now().plusDays(10))
        private val newDialogmoteAvlyst2 =
            generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 2", LocalDateTime.now().plusDays(20))
        private val newDialogmoteInnkalt =
            generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 3", LocalDateTime.now().plusDays(30))
        private val dialogmoteDTOList = listOf(
            newDialogmoteLukket,
            newDialogmoteAvlyst1,
            newDialogmoteAvlyst2,
            newDialogmoteInnkalt
        )

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                var createdDialogmoteUUID = ""
                var createdDialogmoteDeltakerArbeidstakerUUID = ""
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )

                for (dialogmoteDTO in dialogmoteDTOList) {
                    client.postMote(validTokenVeileder, dialogmoteDTO)

                    client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                        assertEquals(HttpStatusCode.OK, status)
                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        val dto = dialogmoteList.first()
                        assertEquals(Dialogmote.Status.INNKALT.name, dto.status)
                        createdDialogmoteUUID = dto.uuid
                        createdDialogmoteDeltakerArbeidstakerUUID = dto.arbeidstaker.uuid
                    }

                    if (dialogmoteDTO != newDialogmoteInnkalt && dialogmoteDTO != newDialogmoteLukket) {
                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"

                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validTokenVeileder)
                            contentType(ContentType.Application.Json)
                            setBody(generateAvlysDialogmoteDTO())
                        }.apply {
                            assertEquals(HttpStatusCode.OK, status)
                        }
                    }
                    if (dialogmoteDTO == newDialogmoteLukket) {
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
                    }
                }

                val createdArbeidstakerBrevUUID: String

                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(5, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerBrevDTO.brevType)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNull(arbeidstakerBrevDTO.lestDato)
                    assertEquals(createdDialogmoteDeltakerArbeidstakerUUID, arbeidstakerBrevDTO.deltakerUuid)

                    createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlArbeidstakerBrevUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                client.post(urlArbeidstakerBrevUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(5, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(MotedeltakerVarselType.INNKALT.name, arbeidstakerBrevDTO.brevType)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNotNull(arbeidstakerBrevDTO.lestDato)
                    assertEquals(
                        newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer,
                        arbeidstakerBrevDTO.virksomhetsnummer
                    )
                    assertEquals(createdDialogmoteDeltakerArbeidstakerUUID, arbeidstakerBrevDTO.deltakerUuid)

                    assertEquals(newDialogmoteInnkalt.tidSted.sted, arbeidstakerBrevDTO.sted)
                    val isCorrectDialogmotetid =
                        LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                    assertTrue(isCorrectDialogmotetid)
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

                val createdReferatArbeidstakerBrevUUID: String
                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(6, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidstakerBrevDTO.brevType)
                    assertTrue(arbeidstakerBrevDTO.digitalt)
                    assertNull(arbeidstakerBrevDTO.lestDato)
                    assertEquals(
                        newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer,
                        arbeidstakerBrevDTO.virksomhetsnummer
                    )
                    assertEquals(createdDialogmoteDeltakerArbeidstakerUUID, arbeidstakerBrevDTO.deltakerUuid)
                    assertEquals(newDialogmoteInnkalt.tidSted.sted, arbeidstakerBrevDTO.sted)
                    val isCorrectDialogmotetid =
                        LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                    assertTrue(isCorrectDialogmotetid)
                    createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlReferatUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                client.post(urlReferatUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(6, arbeidstakerBrevList.size)

                    arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidstakerBrevDTO!!.brevType)
                    assertNotNull(arbeidstakerBrevDTO!!.lestDato)
                }

                client.post(urlReferatUUIDLes) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(6, arbeidstakerBrevList.size)

                    val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                    assertNotNull(arbeidstakerBrevUpdatedDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidstakerBrevUpdatedDTO.brevType)
                    assertEquals(arbeidstakerBrevDTO!!.lestDato, arbeidstakerBrevUpdatedDTO.lestDato)
                }

                val urlPdfForInnkallingNedlasting =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                client.get(urlPdfForInnkallingNedlasting) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfInnkalling, pdfContent)
                }

                val urlPdfForReferatNedlasting =
                    "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                client.get(urlPdfForReferatNedlasting) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfReferat, pdfContent)
                }
            }
        }
    }

    @Nested
    @DisplayName("Uautorisert person nektes tilgang")
    inner class UautorisertPersonNektesTilgang {
        private val newDialogmoteInnkalt =
            generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted", LocalDateTime.now().plusDays(30))

        private val validTokenSelvbetjeningAnnenPerson = generateJWTTokenx(
            audience = externalMockEnvironment.environment.tokenxClientId,
            issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            pid = ARBEIDSTAKER_ANNEN_FNR.value,
        )

        @Test
        fun `should return Forbidden when bearer header contains token for unauthorized person`() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                )
                val createdDialogmoteUUID = client.postAndGetDialogmote(validTokenVeileder, newDialogmoteInnkalt).uuid
                val createdArbeidstakerBrevUUID: String

                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                    assertEquals(1, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlArbeidstakerVarselUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                client.post(urlArbeidstakerVarselUUIDLes) {
                    bearerAuth(validTokenSelvbetjeningAnnenPerson)
                }.apply {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }

                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjeningAnnenPerson)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                    assertEquals(0, arbeidstakerBrevList.size)
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

                val createdReferatArbeidstakerBrevUUID: String
                client.get(arbeidstakerBrevApiPath) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                    assertEquals(2, arbeidstakerBrevList.size)

                    val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                    assertNotNull(arbeidstakerBrevDTO)
                    assertEquals(MotedeltakerVarselType.REFERAT.name, arbeidstakerBrevDTO.brevType)
                    createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                }

                val urlReferatUUIDLes =
                    "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                client.post(urlReferatUUIDLes) {
                    bearerAuth(validTokenSelvbetjeningAnnenPerson)
                }.apply {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }

                val urlPdfForInnkallingNedlasting =
                    "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                client.get(urlPdfForInnkallingNedlasting) {
                    bearerAuth(validTokenSelvbetjeningAnnenPerson)
                }.apply {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }
                client.get(urlPdfForInnkallingNedlasting) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfInnkalling, pdfContent)
                }

                val urlPdfForReferatNedlasting =
                    "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                client.get(urlPdfForReferatNedlasting) {
                    bearerAuth(validTokenSelvbetjeningAnnenPerson)
                }.apply {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }
                client.get(urlPdfForReferatNedlasting) {
                    bearerAuth(validTokenSelvbetjening)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val pdfContent = bodyAsChannel().toByteArray()
                    assertArrayEquals(pdfReferat, pdfContent)
                }
            }
        }
    }
}
