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
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.dialogmelding.domain.ForesporselType
import no.nav.syfo.dialogmelding.domain.SvarType
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteAvlysPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteTidStedPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class DialogmeldingServiceSpek : Spek({
    describe(DialogmeldingServiceSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )
        val dialogmeldingService = DialogmeldingService(
            behandlerVarselService = behandlerVarselService
        )

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        database.dropData()

        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        clearMocks(altinnMock)
        every {
            altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
        } returns altinnResponse

        val validToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            UserConstants.VEILEDER_IDENT,
        )

        describe("Håndterer dialogmelding-svar fra behandler på innkalling til dialogmøte") {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
            lateinit var createdBehandlerVarselInnkallingUuid: String
            lateinit var createdDialogmoteUUID: String

            beforeGroup {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        behandlerVarselService = behandlerVarselService,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    client.postMote(validToken, newDialogmoteDTO)

                    val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val behandlerVarselDTO = dialogmoteList.first().behandler!!.varselList.first()
                    createdBehandlerVarselInnkallingUuid = behandlerVarselDTO.uuid
                    createdDialogmoteUUID = dialogmoteList.first().uuid
                }
            }

            afterGroup {
                database.dropData()
            }

            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer til innkalling-varsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    innkallingVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                    val varselSvarDTO = innkallingVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer ikke til innkalling-varsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    innkallingVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                    val varselSvarDTO = innkallingVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.NYTT_TID_STED.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og mangler conversationRef") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    innkallingVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                    val varselSvarDTO = innkallingVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER_IKKE.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og mangler foresporsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val innkallingVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    innkallingVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                    val varselSvarDTO = innkallingVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER_IKKE.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og conversationRef refererer til innkalling-varsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og mangler conversationRef") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen behandler og mangler conversationRef") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding mangler conversationRef og møtet er avlyst") {
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
                        status shouldBeEqualTo HttpStatusCode.OK
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.find { it.varselType == MotedeltakerVarselType.INNKALT.name }!!.svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
        }
        describe("Håndterer dialogmelding-svar fra behandler på endring av dialogmøte") {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
            lateinit var createdBehandlerVarselInnkallingUuid: String
            lateinit var createdBehandlerVarselEndringUuid: String
            lateinit var createdDialogmoteUUID: String

            beforeGroup {
                testApplication {
                    val client = setupApiAndClient(
                        behandlerVarselService = behandlerVarselService,
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    client.postMote(validToken, newDialogmoteDTO)
                    val response = client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR)

                    response.status shouldBeEqualTo HttpStatusCode.OK

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
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    client.getDialogmoter(validToken, UserConstants.ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK

                        val varselList = body<List<DialogmoteDTO>>().first().behandler!!.varselList
                        createdBehandlerVarselEndringUuid = varselList.first().uuid
                    }
                }
            }

            afterGroup {
                database.dropData()
            }

            it("Oppretter varsel-svar når dialogmelding inneholder parentRef som refererer til endring-varsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    endringVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.NYTT_TID_STED.name
                    val varselSvarDTO = endringVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker og conversationRef refererer til innkalling-varsel og finnes endrings-varsel for arbeidstaker i samme møte") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    endringVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.NYTT_TID_STED.name
                    val varselSvarDTO = endringVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.NYTT_TID_STED.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef og conversationRef referer ikke til innkalling- eller endring-varsler") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    endringVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.NYTT_TID_STED.name
                    val varselSvarDTO = endringVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER_IKKE.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef og conversationRef mangler") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    endringVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.NYTT_TID_STED.name
                    val varselSvarDTO = endringVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER_IKKE.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter varsel-svar når dialogmelding inneholder aktuell arbeidstaker, men parentRef, conversationRef og foresporsel mangler") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()

                    val endringVarsel = dialogmoteList.first().behandler!!.varselList.first()
                    endringVarsel.varselType shouldBeEqualTo MotedeltakerVarselType.NYTT_TID_STED.name
                    val varselSvarDTO = endringVarsel.svar.first()
                    varselSvarDTO.uuid shouldNotBe null
                    varselSvarDTO.createdAt shouldNotBe null
                    varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER_IKKE.name
                    varselSvarDTO.tekst shouldBeEqualTo svarTekst
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og parentRef refererer til endring-varsel") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen arbeidstaker og parentRef og conversationRef mangler") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding inneholder annen behandler og parentRef og conversationRef mangler") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.any { svar -> svar.tekst == svarTekst } shouldBeEqualTo false
                }
            }
        }
        describe("Unhappy paths") {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)

            beforeGroup {
                testApplication {
                    val client = setupApiAndClient(
                        behandlerVarselService = behandlerVarselService,
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    client.postMote(validToken, newDialogmoteDTO)
                }
            }

            it("Oppretter ikke varsel-svar når dialogmelding mangler innkalling-møterespons") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.size shouldBeEqualTo 0
                }
            }
            it("Oppretter ikke varsel-svar når dialogmelding ikke har msgType DIALOG_SVAR") {
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

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val dialogmoteList = response.body<List<DialogmoteDTO>>()
                    val svarList = dialogmoteList.first().behandler!!.varselList.first().svar
                    svarList.size shouldBeEqualTo 0
                }
            }

            afterGroup {
                database.dropData()
            }
        }
    }
})
