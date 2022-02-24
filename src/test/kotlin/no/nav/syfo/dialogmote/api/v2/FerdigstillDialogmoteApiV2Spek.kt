package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmote.PdfService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.database.getReferat
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

val objectMapper: ObjectMapper = apiConsumerObjectMapper()

class FerdigstillDialogmoteApiV2Spek : Spek({

    describe(FerdigstillDialogmoteApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )
            val pdfService = PdfService(
                database = database,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
                clearAllMocks()
                justRun { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                justRun { mqSenderMock.sendMQMessage(any(), any()) }
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
            }

            describe("Ferdigstill Dialogmote") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO()

                    it("should return OK if request is successful") {

                        val createdDialogmote = createDialogmote(
                            validToken,
                            newDialogmoteDTO,
                        )
                        val createdDialogmoteUUID = createdDialogmote.uuid
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.REFERAT, any()) }
                        }

                        val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted

                            val referat = dialogmoteDTO.referat!!
                            referat.digitalt shouldBeEqualTo true
                            referat.situasjon shouldBeEqualTo "Dette er en beskrivelse av situasjonen"
                            referat.behandlerOppgave shouldBeEqualTo null
                            referat.narmesteLederNavn shouldBeEqualTo "Grønn Bamse"
                            referat.document[0].type shouldBeEqualTo DocumentComponentType.HEADER
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
                            pdf shouldBeEqualTo externalMockEnvironment.isdialogmotepdfgenMock.pdfReferat

                            verify(exactly = 1) { brukernotifikasjonProducer.sendBeskjed(any(), any()) }
                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 2

                            moteStatusEndretList.forEach { moteStatusEndret ->
                                moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                                moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                            }
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                            response.content shouldBeEqualTo "Failed to Ferdigstille Dialogmote, already Ferdigstilt"
                        }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTO()
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                            response.content shouldBeEqualTo "Failed to Avlys Dialogmote: already Ferdigstilt"
                        }

                        val urlMoteUUIDPostTidSted =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                        val endreTidStedDialogMoteDto = generateEndreDialogmoteTidStedDTO()
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(endreTidStedDialogMoteDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                            response.content shouldBeEqualTo "Failed to change tid/sted, already Ferdigstilt"
                        }
                    }
                }
                describe("Happy path: with behandler") {
                    val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val endreTidStedBehandlerVarselUUID: String?
                        val referatBehandlerVarselUUID: String?

                        val createdDialogmote = createDialogmote(
                            validToken,
                            newDialogmoteDTO,
                        )
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        clearMocks(behandlerDialogmeldingProducer)
                        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        val createdDialogmoteUUID = createdDialogmote.uuid
                        val innkallingBehandlerVarselUUID = createdDialogmote.behandler?.varselList?.lastOrNull()?.uuid

                        val urlMoteUUIDPostTidSted =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                        val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTOWithBehandler()

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                            clearMocks(behandlerDialogmeldingProducer)
                            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                            val dialogmoteDTO = dialogmoteList.first()
                            endreTidStedBehandlerVarselUUID = dialogmoteDTO.behandler?.varselList?.firstOrNull()?.uuid
                        }

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.REFERAT, any()) }
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referat = dialogmoteDTO.referat!!
                            referatBehandlerVarselUUID = referat.uuid
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.behandler!!.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
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
                    }
                }
                describe("Happy path: with behandler og mellomlagring") {
                    val behandlerOppgave = "Dette er en beskrivelse av behandlers oppgave"
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO(behandlerOppgave = behandlerOppgave)

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmote = createDialogmote(
                            validToken,
                            newDialogmoteDTO,
                        )
                        val createdDialogmoteUUID = createdDialogmote.uuid
                        val innkallingBehandlerVarselUUID = createdDialogmote.behandler?.varselList?.lastOrNull()?.uuid
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        clearMocks(behandlerDialogmeldingProducer)
                        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                        val urlMoteUUIDMellomlagre =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteMellomlagrePath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDMellomlagre) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.REFERAT, any()) }
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referat = dialogmoteDTO.referat!!
                            referat.ferdigstilt shouldBeEqualTo false
                            referat.konklusjon shouldBeEqualTo "Dette er en beskrivelse av konklusjon"
                            referat.andreDeltakere[0].navn shouldBeEqualTo "Tøff Pyjamas"
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        }

                        val modfisertReferat = generateModfisertReferatDTO(behandlerOppgave = behandlerOppgave)
                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(modfisertReferat))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.REFERAT, any()) }
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            val referat = dialogmoteDTO.referat!!
                            val referatBehandlerVarselUUID = referat.uuid
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.behandler!!.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            referat.behandlerOppgave shouldBeEqualTo behandlerOppgave
                            referat.ferdigstilt shouldBeEqualTo true
                            referat.andreDeltakere.size shouldBeEqualTo 1
                            referat.andreDeltakere[0].navn shouldBeEqualTo "Tøffere Pyjamas"
                            referat.konklusjon shouldBeEqualTo "Dette er en beskrivelse av konklusjon modifisert"

                            val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                            verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(capture(kafkaBehandlerDialogmeldingDTOSlot)) }
                            val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                            kafkaBehandlerDialogmeldingDTO.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingUuid shouldBeEqualTo referatBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newReferatDTO.document.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.REFERAT.value
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo innkallingBehandlerVarselUUID
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }
                }
                describe("Happy path: ferdigstilling gjøres av annen bruker enn den som gjør innkalling") {
                    val validToken2 = generateJWT(
                        externalMockEnvironment.environment.aadAppClient,
                        externalMockEnvironment.wellKnownVeilederV2.issuer,
                        VEILEDER_IDENT_2,
                    )
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val newReferatDTO = generateNewReferatDTO()

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmote = createDialogmote(
                            validToken,
                            newDialogmoteDTO,
                        )
                        val createdDialogmoteUUID = createdDialogmote.uuid
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        createdDialogmote.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken2))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newReferatDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT_2
                        }
                    }
                }
            }
        }
    }
})

private fun TestApplicationEngine.createDialogmote(
    validToken: String,
    newDialogmoteDTO: NewDialogmoteDTO,
): DialogmoteDTO {
    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"
    with(
        handleRequest(HttpMethod.Post, urlMoter) {
            addHeader(Authorization, bearerHeader(validToken))
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.OK
    }

    with(
        handleRequest(HttpMethod.Get, urlMoter) {
            addHeader(Authorization, bearerHeader(validToken))
            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.OK

        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

        dialogmoteList.size shouldBeEqualTo 1

        val dialogmoteDTO = dialogmoteList.first()
        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
        dialogmoteDTO.referat shouldBeEqualTo null

        return dialogmoteDTO
    }
}
