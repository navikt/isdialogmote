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
import no.nav.syfo.client.person.oppfolgingstilfelle.toOppfolgingstilfellePerson
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.domain.ForesporselType
import no.nav.syfo.dialogmelding.domain.SvarType
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.kOppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.*
import org.junit.Assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class PostDialogmoteTidStedApiV2Spek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmoteTidStedApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
            val mqSenderMock = mockk<MQSenderInterface>(relaxed = true)
            justRun { mqSenderMock.sendMQMessage(any(), any()) }
            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )
            val dialogmeldingService = DialogmeldingService(
                behandlerVarselService = behandlerVarselService
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
            }
            beforeEachTest {
                justRun { mqSenderMock.sendMQMessage(any(), any()) }
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
            }

            describe("Post DialogmoteTidSted") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
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

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) {
                                mqSenderMock.sendMQMessage(
                                    MotedeltakerVarselType.NYTT_TID_STED,
                                    any()
                                )
                            }
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
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.NYTT_TID_STED.name

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                                it.varselType == MotedeltakerVarselType.NYTT_TID_STED.name
                            }
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo "begrunnelse arbeidstaker"
                            arbeidstakerVarselDTO.document.isEmpty() shouldNotBeEqualTo true
                            arbeidstakerVarselDTO.document.first().texts shouldBeEqualTo listOf("dokumenttekst arbeidstaker")

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                                it.varselType == MotedeltakerVarselType.NYTT_TID_STED.name
                            }
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "begrunnelse arbeidsgiver"
                            arbeidsgiverVarselDTO.document.isEmpty() shouldNotBeEqualTo true
                            arbeidsgiverVarselDTO.document.first().texts shouldBeEqualTo listOf("dokumenttekst arbeidsgiver")

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteTidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/zyx"

                            verify(exactly = 2) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 2

                            moteStatusEndretList.forEach { moteStatusEndret ->
                                moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                                moteStatusEndret.tilfelleStart shouldBeEqualTo kOppfolgingstilfellePersonDTO().toOppfolgingstilfellePerson().fom
                            }
                        }
                    }
                }
                describe("With behandler") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
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

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

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

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) {
                                mqSenderMock.sendMQMessage(
                                    MotedeltakerVarselType.NYTT_TID_STED,
                                    any()
                                )
                            }
                        }

                        val createdDialogmote: DialogmoteDTO
                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            createdDialogmote = dialogmoteList.first()
                            createdDialogmote.status shouldBeEqualTo DialogmoteStatus.NYTT_TID_STED.name
                            verify(exactly = 2) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

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
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newDialogmoteTidSted.behandler!!.endringsdokument.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.TIDSTED.value
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo createdDialogmote.behandler!!.varselList[1].uuid
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo createdDialogmote.behandler!!.varselList[1].uuid
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }

                        clearAllMocks()
                        justRun { mqSenderMock.sendMQMessage(any(), any()) }
                        justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

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
                            conversationRef = createdDialogmote.behandler!!.varselList[1].uuid,
                            parentRef = createdDialogmote.behandler!!.varselList[0].uuid,
                            innkallingMoterespons = innkallingMoterespons,
                        )
                        dialogmeldingService.handleDialogmelding(dialogmeldingDTO)

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) {
                                mqSenderMock.sendMQMessage(
                                    MotedeltakerVarselType.NYTT_TID_STED,
                                    any()
                                )
                            }
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
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.NYTT_TID_STED.name

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
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newDialogmoteTidSted.behandler!!.endringsdokument.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.TIDSTED.value
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo dialogmeldingDTO.msgId
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefConversation shouldBeEqualTo dialogmeldingDTO.conversationRef
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }
                    it("should throw exception if mote with behandler and endring missing behandler") {
                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
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

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

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

                        Assert.assertThrows(RuntimeException::class.java) {
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidStedNoBehandler))
                            }
                        }.message shouldBeEqualTo "Failed to change tid/sted: missing behandler"
                    }
                }
            }
        }
    }
})
