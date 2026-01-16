package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.endpoints.*
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.getReferat
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.mock.pdfReferat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class FerdigstillDialogmoteApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()

    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )
    private val pdfRepository = externalMockEnvironment.pdfRepository

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
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
        clearAllMocks()

        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO()

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )

                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )

                val createdDialogmoteUUID = createdDialogmote.uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply { assertEquals(HttpStatusCode.OK, status) }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )

                assertEquals(newDialogmoteDTO.tidSted.sted, dialogmoteDTO.sted)

                val referat = dialogmoteDTO.referatList.first()
                assertTrue(referat.digitalt)
                assertEquals("Dette er en beskrivelse av situasjonen", referat.situasjon)
                assertNull(referat.behandlerOppgave)
                assertEquals("Grønn Bamse", referat.narmesteLederNavn)
                assertEquals(DocumentComponentType.HEADER_H1, referat.document[0].type)
                assertEquals(listOf("Tittel referat"), referat.document[0].texts)

                assertEquals(DocumentComponentType.PARAGRAPH, referat.document[1].type)
                assertEquals(listOf("Brødtekst"), referat.document[1].texts)

                assertEquals(DocumentComponentType.PARAGRAPH, referat.document[2].type)
                assertEquals("Standardtekst", referat.document[2].key)
                assertEquals(listOf("Dette er en standardtekst"), referat.document[2].texts)

                assertEquals("Verneombud", referat.andreDeltakere.first().funksjon)
                assertEquals("Tøff Pyjamas", referat.andreDeltakere.first().navn)

                assertTrue(referat.ferdigstilt)

                val pdf =
                    pdfRepository.getPdf(database.getReferat(UUID.fromString(referat.uuid)).first().pdfId!!).pdf
                assertArrayEquals(pdfReferat, pdf)

                val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                assertEquals(2, moteStatusEndretList.size)

                moteStatusEndretList.forEach { moteStatusEndret ->
                    assertEquals(VEILEDER_IDENT, moteStatusEndret.opprettetAv)
                    assertEquals(
                        oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start,
                        moteStatusEndret.tilfelleStart
                    )
                }

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to Ferdigstille Dialogmote, already Ferdigstilt"))
                }

                val urlMoteUUIDAvlys =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(generateAvlysDialogmoteDTO())
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to Avlys Dialogmote: already Ferdigstilt"))
                }

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(generateEndreDialogmoteTidStedDTO())
                }.apply {
                    assertEquals(HttpStatusCode.Conflict, status)
                    assertTrue(bodyAsText().contains("Failed to change tid/sted, already Ferdigstilt"))
                }
            }
        }
    }

    @Nested
    @DisplayName("Happy path: with behandler")
    inner class HappyPathWithBehandler {
        private val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

        @Test
        fun `should return OK if request is successful`() {
            var endreTidStedBehandlerVarselUUID: String?
            var referatBehandlerVarselUUID: String?

            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )

                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )

                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                val createdDialogmoteUUID = createdDialogmote.uuid
                val innkallingBehandlerVarselUUID = createdDialogmote.behandler?.varselList?.lastOrNull()?.uuid

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTOWithBehandler()

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                    clearMocks(behandlerDialogmeldingProducer)
                    justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    val dialogmoteDTO = dialogmoteList.first()
                    endreTidStedBehandlerVarselUUID = dialogmoteDTO.behandler?.varselList?.firstOrNull()?.uuid
                }

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    val referat = dialogmoteDTO.referatList.first()
                    referatBehandlerVarselUUID = referat.uuid
                    assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
                    val behandlerDeltaker = dialogmoteDTO.behandler!!
                    assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, behandlerDeltaker.behandlerRef)
                    assertTrue(behandlerDeltaker.mottarReferat)
                    assertTrue(behandlerDeltaker.deltatt)
                    assertEquals(behandlerOppgave, referat.behandlerOppgave)

                    val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                    verify(exactly = 1) {
                        behandlerDialogmeldingProducer.sendDialogmelding(
                            capture(
                                kafkaBehandlerDialogmeldingDTOSlot
                            )
                        )
                    }
                    val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                    assertEquals(
                        newDialogmoteDTO.behandler!!.behandlerRef,
                        kafkaBehandlerDialogmeldingDTO.behandlerRef
                    )
                    assertEquals(referatBehandlerVarselUUID, kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid)
                    assertEquals(
                        newReferatDTO.document.serialize(),
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                    )
                    assertEquals(
                        DialogmeldingType.DIALOG_NOTAT.name,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType
                    )
                    assertEquals(DialogmeldingKode.REFERAT.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                    assertNotEquals(endreTidStedBehandlerVarselUUID, innkallingBehandlerVarselUUID)
                    assertNotEquals(endreTidStedBehandlerVarselUUID, referatBehandlerVarselUUID)
                    assertNotEquals(referatBehandlerVarselUUID, innkallingBehandlerVarselUUID)
                    assertEquals(
                        endreTidStedBehandlerVarselUUID,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent
                    )
                    assertEquals(
                        innkallingBehandlerVarselUUID,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation
                    )
                    assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
                }

                val endretReferatDTO = generateNewReferatDTO(
                    behandlerOppgave = "Endret oppgave for behandler",
                    begrunnelseEndring = "Dette er en begrunnelse",
                )
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                val urlMoteUUIDEndreFerdigstilt =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteEndreFerdigstiltPath"

                client.post(urlMoteUUIDEndreFerdigstilt) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(endretReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    val referatList = dialogmoteDTO.referatList
                    assertEquals(2, referatList.size)
                    val referat = referatList.first()
                    assertEquals(endretReferatDTO.behandlerOppgave, referat.behandlerOppgave)
                    assertEquals(endretReferatDTO.begrunnelseEndring, referat.begrunnelseEndring)
                    val newReferatBehandlerVarselUUID = referat.uuid
                    assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)

                    val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                    verify(exactly = 1) {
                        behandlerDialogmeldingProducer.sendDialogmelding(
                            capture(
                                kafkaBehandlerDialogmeldingDTOSlot
                            )
                        )
                    }
                    val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                    assertEquals(
                        newDialogmoteDTO.behandler!!.behandlerRef,
                        kafkaBehandlerDialogmeldingDTO.behandlerRef
                    )
                    assertEquals(newReferatBehandlerVarselUUID, kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid)
                    assertEquals(
                        endretReferatDTO.document.serialize(),
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                    )
                    assertEquals(
                        DialogmeldingType.DIALOG_NOTAT.name,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType
                    )
                    assertEquals(DialogmeldingKode.REFERAT.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                    assertNotEquals(endreTidStedBehandlerVarselUUID, innkallingBehandlerVarselUUID)
                    assertNotEquals(endreTidStedBehandlerVarselUUID, referatBehandlerVarselUUID)
                    assertNotEquals(referatBehandlerVarselUUID, innkallingBehandlerVarselUUID)
                    assertEquals(
                        endreTidStedBehandlerVarselUUID,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent
                    )
                    assertEquals(
                        innkallingBehandlerVarselUUID,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation
                    )
                    assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
                }
            }
        }
    }

    @Nested
    @DisplayName("Happy path: with behandler (ikke deltatt, ikke motta referat)")
    inner class HappyPathWithBehandlerIkkeDeltattIkkeMottaReferat {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = false)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )
                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                val createdDialogmoteUUID = createdDialogmote.uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                val behandlerDeltaker = dialogmoteDTO.behandler!!
                assertFalse(behandlerDeltaker.mottarReferat)
                assertFalse(behandlerDeltaker.deltatt)
            }
        }
    }

    @Nested
    @DisplayName("Happy path: with behandler (ikke deltatt, motta referat)")
    inner class HappyPathWithBehandlerIkkeDeltattMottaReferat {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = true)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )
                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                val createdDialogmoteUUID = createdDialogmote.uuid

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                val behandlerDeltaker = dialogmoteDTO.behandler!!
                assertTrue(behandlerDeltaker.mottarReferat)
                assertFalse(behandlerDeltaker.deltatt)
            }
        }
    }

    @Nested
    @DisplayName("Happy path: with behandler og mellomlagring")
    inner class HappyPathWithBehandlerOgMellomlagring {
        private val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )

                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )
                val createdDialogmoteUUID = createdDialogmote.uuid
                val urlMoteUUIDMellomlagre =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteMellomlagrePath"
                val innkallingBehandlerVarselUUID = createdDialogmote.behandler?.varselList?.lastOrNull()?.uuid
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                client.post(urlMoteUUIDMellomlagre) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    val referat = dialogmoteDTO.referatList.first()
                    assertFalse(referat.ferdigstilt)
                    assertEquals("Dette er en beskrivelse av konklusjon", referat.konklusjon)
                    assertEquals("Tøff Pyjamas", referat.andreDeltakere[0].navn)
                    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)
                }

                val modfisertReferat = generateModfisertReferatDTO(behandlerOppgave = behandlerOppgave)
                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(modfisertReferat)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                val referat = dialogmoteDTO.referatList.first()
                val referatBehandlerVarselUUID = referat.uuid
                assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
                val behandlerDeltaker = dialogmoteDTO.behandler!!
                assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, behandlerDeltaker.behandlerRef)
                assertTrue(behandlerDeltaker.mottarReferat)
                assertTrue(behandlerDeltaker.deltatt)
                assertEquals(behandlerOppgave, referat.behandlerOppgave)
                assertTrue(referat.ferdigstilt)
                assertEquals(1, referat.andreDeltakere.size)
                assertEquals("Tøffere Pyjamas", referat.andreDeltakere[0].navn)
                assertEquals("Dette er en beskrivelse av konklusjon modifisert", referat.konklusjon)

                val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                verify(exactly = 1) {
                    behandlerDialogmeldingProducer.sendDialogmelding(
                        capture(
                            kafkaBehandlerDialogmeldingDTOSlot
                        )
                    )
                }
                val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, kafkaBehandlerDialogmeldingDTO.behandlerRef)
                assertEquals(referatBehandlerVarselUUID, kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid)
                assertEquals(newReferatDTO.document.serialize(), kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst)
                assertEquals(DialogmeldingType.DIALOG_NOTAT.name, kafkaBehandlerDialogmeldingDTO.dialogmeldingType)
                assertEquals(
                    DialogmeldingKodeverk.HENVENDELSE.name,
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk
                )
                assertEquals(DialogmeldingKode.REFERAT.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                assertEquals(
                    innkallingBehandlerVarselUUID,
                    kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation
                )
                assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
            }
        }
    }

    @Nested
    @DisplayName("Happy path: with behandler og mellomlagring (ikke deltatt, ikke motta referat)")
    inner class HappyPathWithBehandlerOgMellomlagringIkkeDeltattIkkeMottaReferat {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = false)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )
                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )
                val createdDialogmoteUUID = createdDialogmote.uuid
                verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                val urlMoteUUIDMellomlagre =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteMellomlagrePath"

                client.post(urlMoteUUIDMellomlagre) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                val behandlerDeltaker = dialogmoteDTO.behandler!!
                assertFalse(behandlerDeltaker.mottarReferat)
                assertFalse(behandlerDeltaker.deltatt)
            }
        }
    }

    @Nested
    @DisplayName("Happy path: ferdigstilling gjøres av annen bruker enn den som gjør innkalling")
    inner class HappyPathFerdigstillingAvAnnenBruker {
        private val validToken2 = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT_2,
        )
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        private val newReferatDTO = generateNewReferatDTO()

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                )

                val createdDialogmote = client.postAndGetDialogmote(
                    validToken,
                    newDialogmoteDTO,
                )
                val createdDialogmoteUUID = createdDialogmote.uuid
                assertEquals(VEILEDER_IDENT, createdDialogmote.tildeltVeilederIdent)

                val urlMoteUUIDFerdigstill =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                client.post(urlMoteUUIDFerdigstill) {
                    bearerAuth(validToken2)
                    contentType(ContentType.Application.Json)
                    setBody(newReferatDTO)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(Dialogmote.Status.FERDIGSTILT.name, dialogmoteDTO.status)
                assertEquals(VEILEDER_IDENT_2, dialogmoteDTO.tildeltVeilederIdent)
            }
        }
    }
}
