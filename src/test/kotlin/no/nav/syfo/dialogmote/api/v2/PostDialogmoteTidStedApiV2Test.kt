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
import no.nav.syfo.api.dto.EndreTidStedBegrunnelseDTO
import no.nav.syfo.api.dto.EndreTidStedDialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.DialogmeldingService
import no.nav.syfo.domain.ForesporselType
import no.nav.syfo.domain.SvarType
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.infrastructure.kafka.esyfovarsel.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.*

class PostDialogmoteTidStedApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val esyfovarselEndringHendelse = generateEndringHendelse()
    private val moteStatusEndretRepository = MoteStatusEndretRepository(database)

    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()

    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )
    private val dialogmeldingService = DialogmeldingService(
        behandlerVarselService = behandlerVarselService
    )

    private val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
    private val altinnResponse = ReceiptExternal()

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }

        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearAllMocks()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )

                client.postMote(validToken, newDialogmoteDTO).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    esyfovarselEndringHendelse.type = HendelseType.NL_DIALOGMOTE_INNKALT
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(DialogmoteStatus.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidSted = EndreTidStedDialogmoteDTO(
                    sted = "Et annet sted",
                    tid = newDialogmoteDTO.tidSted.tid.plusDays(1),
                    videoLink = "https://meet.google.com/zyx",
                    arbeidstaker = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidstaker",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidstaker"),
                            )
                        )
                    ),
                    arbeidsgiver = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidsgiver",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidsgiver"),
                            )
                        )
                    ),
                    behandler = null,
                )

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }
                }

                val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                assertEquals(1, dialogmoteList.size)

                val dialogmoteDTO = dialogmoteList.first()
                assertEquals(DialogmoteStatus.NYTT_TID_STED.name, dialogmoteDTO.status)

                assertEquals(newDialogmoteDTO.arbeidstaker.personIdent, dialogmoteDTO.arbeidstaker.personIdent)
                val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                    it.varselType == MotedeltakerVarselType.NYTT_TID_STED.name
                }
                assertNotNull(arbeidstakerVarselDTO)
                assertTrue(arbeidstakerVarselDTO?.digitalt!!)
                assertNull(arbeidstakerVarselDTO.lestDato)
                assertEquals("begrunnelse arbeidstaker", arbeidstakerVarselDTO.fritekst)
                assertFalse(arbeidstakerVarselDTO.document.isEmpty())
                assertEquals(listOf("dokumenttekst arbeidstaker"), arbeidstakerVarselDTO.document.first().texts)

                assertEquals(
                    newDialogmoteDTO.arbeidsgiver.virksomhetsnummer,
                    dialogmoteDTO.arbeidsgiver.virksomhetsnummer
                )
                val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                    it.varselType == MotedeltakerVarselType.NYTT_TID_STED.name
                }
                assertNotNull(arbeidsgiverVarselDTO)
                assertNull(arbeidsgiverVarselDTO?.lestDato)
                assertEquals("begrunnelse arbeidsgiver", arbeidsgiverVarselDTO?.fritekst)
                assertFalse(arbeidsgiverVarselDTO?.document?.isEmpty()!!)
                assertEquals(listOf("dokumenttekst arbeidsgiver"), arbeidsgiverVarselDTO?.document?.first()?.texts)

                assertEquals(newDialogmoteTidSted.sted, dialogmoteDTO.sted)
                assertEquals("https://meet.google.com/zyx", dialogmoteDTO.videoLink)

                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }
                verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }

                val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                assertEquals(2, moteStatusEndretList.size)

                moteStatusEndretList.forEach { moteStatusEndret ->
                    assertEquals(VEILEDER_IDENT, moteStatusEndret.opprettetAv)
                    assertEquals(
                        oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start,
                        moteStatusEndret.tilfelleStart
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("With behandler")
    inner class WithBehandler {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)

        @Test
        fun `should return OK if request is successful`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    esyfovarselEndringHendelse.type = HendelseType.NL_DIALOGMOTE_INNKALT
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            esyfovarselEndringHendelse
                        )
                    }
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                    clearMocks(behandlerDialogmeldingProducer)
                    justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(DialogmoteStatus.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTid = newDialogmoteDTO.tidSted.tid.plusDays(1).truncatedTo(ChronoUnit.SECONDS)
                val newDialogmoteTidSted = EndreTidStedDialogmoteDTO(
                    sted = "Et annet sted",
                    tid = newDialogmoteTid,
                    videoLink = "https://meet.google.com/zyx",
                    arbeidstaker = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidstaker",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidstaker"),
                            )
                        )
                    ),
                    arbeidsgiver = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidsgiver",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidsgiver"),
                            )
                        )
                    ),
                    behandler = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse behandler",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst behandler"),
                            )
                        )
                    ),
                )

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            esyfovarselEndringHendelse
                        )
                    }
                }

                val createdDialogmote: DialogmoteDTO
                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    createdDialogmote = dialogmoteList.first()
                    assertEquals(DialogmoteStatus.NYTT_TID_STED.name, createdDialogmote.status)

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
                    assertEquals(
                        newDialogmoteTidSted.behandler!!.endringsdokument.serialize(),
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                    )
                    assertEquals(
                        DialogmeldingType.DIALOG_FORESPORSEL.name,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType
                    )
                    assertEquals(
                        DialogmeldingKodeverk.DIALOGMOTE.name,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk
                    )
                    assertEquals(DialogmeldingKode.TIDSTED.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                    assertEquals(
                        createdDialogmote.behandler!!.varselList[1].uuid,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent
                    )
                    assertEquals(
                        createdDialogmote.behandler!!.varselList[1].uuid,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation
                    )
                    assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
                }
                clearAllMocks()
                justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }

                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse

                // Ny endring, etter svar fra behandler
                val innkallingMoterespons = generateInnkallingMoterespons(
                    foresporselType = ForesporselType.ENDRING,
                    svarType = SvarType.NYTT_TIDSPUNKT,
                    svarTekst = "Forslag til nytt tidspunkt",
                )
                val msgId = UUID.randomUUID().toString()
                val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                    msgId = msgId,
                    msgType = "DIALOG_SVAR",
                    personIdentPasient = ARBEIDSTAKER_FNR,
                    personIdentBehandler = BEHANDLER_FNR,
                    conversationRef = createdDialogmote.behandler!!.varselList[1].uuid,
                    parentRef = createdDialogmote.behandler!!.varselList[0].uuid,
                    innkallingMoterespons = innkallingMoterespons,
                )
                dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

                client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidSted)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    esyfovarselEndringHendelse.type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            esyfovarselEndringHendelse.copy(
                                data = VarselData(
                                    narmesteLeder = VarselDataNarmesteLeder("narmesteLederNavn"),
                                    motetidspunkt = VarselDataMotetidspunkt(newDialogmoteTid)
                                ),
                            )
                        )
                    }
                }
                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()
                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(DialogmoteStatus.NYTT_TID_STED.name, dialogmoteDTO.status)

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
                    assertEquals(
                        newDialogmoteTidSted.behandler!!.endringsdokument.serialize(),
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst
                    )
                    assertEquals(
                        DialogmeldingType.DIALOG_FORESPORSEL.name,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType
                    )
                    assertEquals(DialogmeldingKode.TIDSTED.value, kafkaBehandlerDialogmeldingDTO.dialogmeldingKode)
                    assertEquals(dialogmeldingDTO.msgId, kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent)
                    assertEquals(
                        dialogmeldingDTO.conversationRef,
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation
                    )
                    assertNotNull(kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg)
                }
            }
        }

        @Test
        fun `should throw exception if mote with behandler and endring missing behandler`() {
            testApplication {
                val createdDialogmoteUUID: String
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO).apply {
                    assertEquals(HttpStatusCode.OK, status)
                    esyfovarselEndringHendelse.type = HendelseType.NL_DIALOGMOTE_INNKALT
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselEndringHendelse) }
                    verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                    clearMocks(behandlerDialogmeldingProducer)
                    justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                }

                client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val dialogmoteList = body<List<DialogmoteDTO>>()

                    assertEquals(1, dialogmoteList.size)

                    val dialogmoteDTO = dialogmoteList.first()
                    assertEquals(DialogmoteStatus.INNKALT.name, dialogmoteDTO.status)

                    createdDialogmoteUUID = dialogmoteDTO.uuid
                }

                val urlMoteUUIDPostTidSted =
                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                val newDialogmoteTidStedNoBehandler = EndreTidStedDialogmoteDTO(
                    sted = "Et annet sted",
                    tid = newDialogmoteDTO.tidSted.tid.plusDays(1),
                    videoLink = "https://meet.google.com/zyx",
                    arbeidstaker = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidstaker",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidstaker"),
                            )
                        )
                    ),
                    arbeidsgiver = EndreTidStedBegrunnelseDTO(
                        begrunnelse = "begrunnelse arbeidsgiver",
                        endringsdokument = listOf(
                            DocumentComponentDTO(
                                type = DocumentComponentType.PARAGRAPH,
                                title = null,
                                texts = listOf("dokumenttekst arbeidsgiver"),
                            )
                        )
                    ),
                    behandler = null,
                )

                val response = client.post(urlMoteUUIDPostTidSted) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteTidStedNoBehandler)
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("Failed to change tid/sted: missing behandler"))
            }
        }
    }
}
