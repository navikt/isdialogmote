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
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmote.PdfService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.getReferat
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.mock.pdfReferat
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class FerdigstillDialogmoteApiV2Spek : Spek({

    describe(FerdigstillDialogmoteApiV2Spek::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )
        val pdfService = PdfService(
            database = database,
        )

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

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
            clearAllMocks()

            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
        }

        describe("Ferdigstill Dialogmote") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO()

                it("should return OK if request is successful") {
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
                        }.apply { status shouldBeEqualTo HttpStatusCode.OK }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted

                        val referat = dialogmoteDTO.referatList.first()
                        referat.digitalt shouldBeEqualTo true
                        referat.situasjon shouldBeEqualTo "Dette er en beskrivelse av situasjonen"
                        referat.behandlerOppgave shouldBeEqualTo null
                        referat.narmesteLederNavn shouldBeEqualTo "Grønn Bamse"
                        referat.document[0].type shouldBeEqualTo DocumentComponentType.HEADER_H1
                        referat.document[0].texts shouldBeEqualTo listOf("Tittel referat")

                        referat.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        referat.document[1].texts shouldBeEqualTo listOf("Brødtekst")

                        referat.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        referat.document[2].key shouldBeEqualTo "Standardtekst"
                        referat.document[2].texts shouldBeEqualTo listOf("Dette er en standardtekst")

                        referat.andreDeltakere.first().funksjon shouldBeEqualTo "Verneombud"
                        referat.andreDeltakere.first().navn shouldBeEqualTo "Tøff Pyjamas"

                        referat.ferdigstilt shouldBeEqualTo true

                        val pdf =
                            pdfService.getPdf(database.getReferat(UUID.fromString(referat.uuid)).first().pdfId!!)
                        pdf shouldBeEqualTo pdfReferat

                        val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                        moteStatusEndretList.size shouldBeEqualTo 2

                        moteStatusEndretList.forEach { moteStatusEndret ->
                            moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                            moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                        }

                        client.post(urlMoteUUIDFerdigstill) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(newReferatDTO)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to Ferdigstille Dialogmote, already Ferdigstilt"
                        }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(generateAvlysDialogmoteDTO())
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to Avlys Dialogmote: already Ferdigstilt"
                        }

                        val urlMoteUUIDPostTidSted =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                        client.post(urlMoteUUIDPostTidSted) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(generateEndreDialogmoteTidStedDTO())
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to change tid/sted, already Ferdigstilt"
                        }
                    }
                }
            }
            describe("Happy path: with behandler") {
                val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

                it("should return OK if request is successful") {
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
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                            clearMocks(behandlerDialogmeldingProducer)
                            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

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
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referat = dialogmoteDTO.referatList.first()
                            referatBehandlerVarselUUID = referat.uuid
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            val behandlerDeltaker = dialogmoteDTO.behandler!!
                            behandlerDeltaker.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            behandlerDeltaker.mottarReferat shouldBeEqualTo true
                            behandlerDeltaker.deltatt shouldBeEqualTo true
                            referat.behandlerOppgave shouldBeEqualTo behandlerOppgave

                            val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                            verify(exactly = 1) {
                                behandlerDialogmeldingProducer.sendDialogmelding(
                                    capture(
                                        kafkaBehandlerDialogmeldingDTOSlot
                                    )
                                )
                            }
                            val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                            kafkaBehandlerDialogmeldingDTO.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid shouldBeEqualTo referatBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newReferatDTO.document.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.REFERAT.value
                            endreTidStedBehandlerVarselUUID shouldNotBeEqualTo innkallingBehandlerVarselUUID
                            endreTidStedBehandlerVarselUUID shouldNotBeEqualTo referatBehandlerVarselUUID
                            referatBehandlerVarselUUID shouldNotBeEqualTo innkallingBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo endreTidStedBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo innkallingBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
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
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()
                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referatList = dialogmoteDTO.referatList
                            referatList.size shouldBeEqualTo 2
                            val referat = referatList.first()
                            referat.behandlerOppgave shouldBeEqualTo endretReferatDTO.behandlerOppgave
                            referat.begrunnelseEndring shouldBeEqualTo endretReferatDTO.begrunnelseEndring
                            val newReferatBehandlerVarselUUID = referat.uuid
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name

                            val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                            verify(exactly = 1) {
                                behandlerDialogmeldingProducer.sendDialogmelding(
                                    capture(
                                        kafkaBehandlerDialogmeldingDTOSlot
                                    )
                                )
                            }
                            val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                            kafkaBehandlerDialogmeldingDTO.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid shouldBeEqualTo newReferatBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo endretReferatDTO.document.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.REFERAT.value
                            endreTidStedBehandlerVarselUUID shouldNotBeEqualTo innkallingBehandlerVarselUUID
                            endreTidStedBehandlerVarselUUID shouldNotBeEqualTo referatBehandlerVarselUUID
                            referatBehandlerVarselUUID shouldNotBeEqualTo innkallingBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo endreTidStedBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo innkallingBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }
                }
            }
            describe("Happy path: with behandler (ikke deltatt, ikke motta referat)") {
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = false)

                it("should return OK if request is successful") {
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
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        val behandlerDeltaker = dialogmoteDTO.behandler!!
                        behandlerDeltaker.mottarReferat shouldBeEqualTo false
                        behandlerDeltaker.deltatt shouldBeEqualTo false
                    }
                }
            }
            describe("Happy path: with behandler (ikke deltatt, motta referat)") {
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = true)

                it("should return OK if request is successful") {
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
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        val behandlerDeltaker = dialogmoteDTO.behandler!!
                        behandlerDeltaker.mottarReferat shouldBeEqualTo true
                        behandlerDeltaker.deltatt shouldBeEqualTo false
                    }
                }
            }
            describe("Happy path: with behandler og mellomlagring") {
                val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

                it("should return OK if request is successful") {
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
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referat = dialogmoteDTO.referatList.first()
                            referat.ferdigstilt shouldBeEqualTo false
                            referat.konklusjon shouldBeEqualTo "Dette er en beskrivelse av konklusjon"
                            referat.andreDeltakere[0].navn shouldBeEqualTo "Tøff Pyjamas"
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        }

                        val modfisertReferat = generateModfisertReferatDTO(behandlerOppgave = behandlerOppgave)
                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                        client.post(urlMoteUUIDFerdigstill) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(modfisertReferat)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        val referat = dialogmoteDTO.referatList.first()
                        val referatBehandlerVarselUUID = referat.uuid
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                        val behandlerDeltaker = dialogmoteDTO.behandler!!
                        behandlerDeltaker.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                        behandlerDeltaker.mottarReferat shouldBeEqualTo true
                        behandlerDeltaker.deltatt shouldBeEqualTo true
                        referat.behandlerOppgave shouldBeEqualTo behandlerOppgave
                        referat.ferdigstilt shouldBeEqualTo true
                        referat.andreDeltakere.size shouldBeEqualTo 1
                        referat.andreDeltakere[0].navn shouldBeEqualTo "Tøffere Pyjamas"
                        referat.konklusjon shouldBeEqualTo "Dette er en beskrivelse av konklusjon modifisert"

                        val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                        verify(exactly = 1) {
                            behandlerDialogmeldingProducer.sendDialogmelding(
                                capture(
                                    kafkaBehandlerDialogmeldingDTOSlot
                                )
                            )
                        }
                        val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                        kafkaBehandlerDialogmeldingDTO.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid shouldBeEqualTo referatBehandlerVarselUUID
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newReferatDTO.document.serialize()
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.REFERAT.value
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo innkallingBehandlerVarselUUID
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                    }
                }
            }
            describe("Happy path: with behandler og mellomlagring (ikke deltatt, ikke motta referat)") {
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO(behandlerDeltatt = false, behandlerMottarReferat = false)

                it("should return OK if request is successful") {
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
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        val behandlerDeltaker = dialogmoteDTO.behandler!!
                        behandlerDeltaker.mottarReferat shouldBeEqualTo false
                        behandlerDeltaker.deltatt shouldBeEqualTo false
                    }
                }
            }
            describe("Happy path: ferdigstilling gjøres av annen bruker enn den som gjør innkalling") {
                val validToken2 = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT_2,
                )
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                val newReferatDTO = generateNewReferatDTO()

                it("should return OK if request is successful") {
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
                        createdDialogmote.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"

                        client.post(urlMoteUUIDFerdigstill) {
                            bearerAuth(validToken2)
                            contentType(ContentType.Application.Json)
                            setBody(newReferatDTO)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT_2
                    }
                }
            }
        }
    }
})
