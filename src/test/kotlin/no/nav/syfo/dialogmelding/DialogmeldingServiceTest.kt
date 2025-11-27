package no.nav.syfo.dialogmelding

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
import no.nav.syfo.api.endpoints.dialogmoteApiMoteAvlysPath
import no.nav.syfo.api.endpoints.dialogmoteApiMoteTidStedPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.DialogmeldingService
import no.nav.syfo.domain.ForesporselType
import no.nav.syfo.domain.SvarType
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import java.util.*

class DialogmeldingServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

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
        UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun setup() {
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK
        database.dropData()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    @Nested
    @DisplayName("Håndterer dialogmelding-svar fra behandler på innkalling til dialogmøte")
    inner class HandlerDialogmeldingSvarFraBehandlerPaInnkalling {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
        private lateinit var createdBehandlerVarselInnkallingUuid: String
        private lateinit var createdDialogmoteUUID: String

        @BeforeEach
        fun setupDialogmote() {
            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)

                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val behandlerVarselDTO = dialogmoteList.first().behandler!!.varselList.first()
                createdBehandlerVarselInnkallingUuid = behandlerVarselDTO.uuid
                createdDialogmoteUUID = dialogmoteList.first().uuid
            }
        }

        @Test
        @DisplayName("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer til innkalling-varsel")
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker`() {
            val svarTekst = "Fastlegen kommer i møtet"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KOMMER,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = createdBehandlerVarselInnkallingUuid,
                parentRef = createdBehandlerVarselInnkallingUuid,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, innkallingVarsel.varselType)
                val varselSvarDTO = innkallingVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer ikke til innkalling-varsel`() {
            val svarTekst = "Fastlegen ønsker nytt tidspunkt"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.NYTT_TIDSPUNKT,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = UUID.randomUUID().toString(),
                parentRef = UUID.randomUUID().toString(),
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, innkallingVarsel.varselType)
                val varselSvarDTO = innkallingVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.NYTT_TID_STED.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og mangler conversationRef`() {
            val svarTekst = "Fastlegen kan ikke komme"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, innkallingVarsel.varselType)
                val varselSvarDTO = innkallingVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER_IKKE.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og mangler foresporsel`() {
            val svarTekst = "Fastlegen kan ikke komme og mangler foresporsel"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = null,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.INNKALT.name, innkallingVarsel.varselType)
                val varselSvarDTO = innkallingVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER_IKKE.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og conversationRef refererer til innkalling-varsel`() {
            val svarTekst = "Fastlegen kommer ei"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_ANNEN_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = createdBehandlerVarselInnkallingUuid,
                parentRef = createdBehandlerVarselInnkallingUuid,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og mangler conversationRef`() {
            val svarTekst = "Fastlegens svar"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KOMMER,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_ANNEN_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen behandler og mangler conversationRef`() {
            val svarTekst = "Annen fastleges svar her"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KOMMER,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_ANNEN_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding mangler conversationRef og møtet er avlyst`() {
            val urlMoteUUIDAvlys = "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
            val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )

                client.post(urlMoteUUIDAvlys) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(avlysDialogMoteDto)
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }

                val svarTekst = "Svar på avlyst møte"
                val innkallingMoterespons = generateInnkallingMoterespons(
                    foresporselType = ForesporselType.INNKALLING,
                    svarType = SvarType.KOMMER,
                    svarTekst = svarTekst,
                )
                val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                    msgType = "DIALOG_SVAR",
                    personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                    personIdentBehandler = UserConstants.BEHANDLER_FNR,
                    conversationRef = null,
                    parentRef = null,
                    innkallingMoterespons = innkallingMoterespons,
                )
                dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList =
                    dialogmoteList.first().behandler!!.varselList.find { it.varselType == MotedeltakerVarselType.INNKALT.name }!!.svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }
    }

    @Nested
    @DisplayName("Håndterer dialogmelding-svar fra behandler på endring av dialogmøte")
    inner class HandlerDialogmeldingSvarFraBehandlerPaEndring {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
        private lateinit var createdBehandlerVarselInnkallingUuid: String
        private lateinit var createdBehandlerVarselEndringUuid: String
        private lateinit var createdDialogmoteUUID: String

        @BeforeEach
        fun setupDialogmoteWithEndring() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                createdDialogmoteUUID = dialogmoteList.first().uuid
                val behandlerVarselDTO = dialogmoteList.first().behandler!!.varselList.first()
                createdBehandlerVarselInnkallingUuid = behandlerVarselDTO.uuid

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

                client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                    assertEquals(HttpStatusCode.OK, status)

                    val varselList = body<List<DialogmoteDTO>>().first().behandler!!.varselList
                    createdBehandlerVarselEndringUuid = varselList.first().uuid
                }
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder parentRef som refererer til endring-varsel`() {
            val svarTekst = "Fastlegen kommer"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.KOMMER,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = createdBehandlerVarselInnkallingUuid,
                parentRef = createdBehandlerVarselEndringUuid,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.NYTT_TID_STED.name, endringVarsel.varselType)
                val varselSvarDTO = endringVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        @DisplayName("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer til innkalling-varsel og finnes endrings-varsel for arbeidstaker i samme møte")
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer til innkalling-varsel`() {
            val svarTekst = "Fastlegen ønsker endret sted"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.NYTT_TIDSPUNKT,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = createdBehandlerVarselInnkallingUuid,
                parentRef = UUID.randomUUID().toString(),
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.NYTT_TID_STED.name, endringVarsel.varselType)
                val varselSvarDTO = endringVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.NYTT_TID_STED.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef og conversationRef referer ikke til innkalling- eller endring-varsler`() {
            val svarTekst = "Fastlegen kommer ikke"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = UUID.randomUUID().toString(),
                parentRef = UUID.randomUUID().toString(),
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.NYTT_TID_STED.name, endringVarsel.varselType)
                val varselSvarDTO = endringVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER_IKKE.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef og conversationRef mangler`() {
            val svarTekst = "Fastlegen kommer ikke"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.NYTT_TID_STED.name, endringVarsel.varselType)
                val varselSvarDTO = endringVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER_IKKE.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef, conversationRef og foresporsel mangler`() {
            val svarTekst = "Fastlegen kommer ikke endring uten foresporsel"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = null,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()

                val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                assertEquals(MotedeltakerVarselType.NYTT_TID_STED.name, endringVarsel.varselType)
                val varselSvarDTO = endringVarsel.svar.first()
                assertNotNull(varselSvarDTO.uuid)
                assertNotNull(varselSvarDTO.createdAt)
                assertEquals(DialogmoteSvarType.KOMMER_IKKE.name, varselSvarDTO.svarType)
                assertEquals(svarTekst, varselSvarDTO.tekst)
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og parentRef refererer til endring-varsel`() {
            val svarTekst = "Fastlegen kan ikke møte på dette tidspunktet"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.NYTT_TIDSPUNKT,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_ANNEN_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = createdBehandlerVarselInnkallingUuid,
                parentRef = createdBehandlerVarselEndringUuid,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og parentRef og conversationRef mangler`() {
            val svarTekst = "Fastlegens svar her"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_ANNEN_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding inneholder annen behandler og parentRef og conversationRef mangler`() {
            val svarTekst = "Annen fastleges svar her"
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.ENDRING,
                svarType = SvarType.KAN_IKKE_KOMME,
                svarTekst = svarTekst,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_ANNEN_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertFalse(svarList.any { svar -> svar.tekst == svarTekst })
            }
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {
        private val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)

        @BeforeEach
        fun setupDialogmote() {
            testApplication {
                val client = setupApiAndClient(
                    behandlerVarselService = behandlerVarselService,
                    altinnMock = altinnMock,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                client.postMote(validToken, newDialogmoteDTO)
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding mangler innkalling-møterespons`() {
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_SVAR",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = null,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertEquals(0, svarList.size)
            }
        }

        @Test
        fun `Oppretter ikke varsel-svar når dialogmelding ikke har msgType DIALOG_SVAR`() {
            val innkallingMoterespons = generateInnkallingMoterespons(
                foresporselType = ForesporselType.INNKALLING,
                svarType = SvarType.KOMMER,
                svarTekst = null,
            )
            val dialogmeldingDTO = generateKafkaDialogmeldingDTO(
                msgType = "DIALOG_NOTAT",
                personIdentPasient = UserConstants.ARBEIDSTAKER_FNR,
                personIdentBehandler = UserConstants.BEHANDLER_FNR,
                conversationRef = null,
                parentRef = null,
                innkallingMoterespons = innkallingMoterespons,
            )
            dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

            testApplication {
                val client = setupApiAndClient(
                    altinnMock = altinnMock,
                    behandlerVarselService = behandlerVarselService,
                    esyfovarselProducer = esyfovarselProducerMock,
                )
                val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                assertEquals(HttpStatusCode.OK, response.status)

                val dialogmoteList = response.body<List<DialogmoteDTO>>()
                val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                assertEquals(0, svarList.size)
            }
        }
    }
}
