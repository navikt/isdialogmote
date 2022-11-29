package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.brev.esyfovarsel.HendelseType
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.domain.ForesporselType
import no.nav.syfo.dialogmelding.domain.SvarType
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.EndreTidStedBegrunnelseDTO
import no.nav.syfo.dialogmote.api.domain.EndreTidStedDialogmoteDTO
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.DialogmeldingKode
import no.nav.syfo.dialogmote.domain.DialogmeldingType
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.dialogmote.domain.serialize
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.generator.generateInnkallingMoterespons
import no.nav.syfo.testhelper.generator.generateKafkaDialogmeldingDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithBehandler
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PostDialogmoteTidStedApiV2Spek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(PostDialogmoteTidStedApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val esyfovarselHendelse = NarmesteLederHendelse(
                type = HendelseType.NL_DIALOGMOTE_INNKALT,
                data = null,
                narmesteLederFnr = "98765432101",
                narmesteLederNavn = "narmesteLederNavn",
                arbeidstakerFnr = "12345678912",
                orgnummer = "934567897"
            )

            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

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

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock,
            )

            afterEachTest {
                database.dropData()
                clearAllMocks()
            }
            beforeEachTest {
                justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            describe("Post DialogmoteTidSted") {
                val validToken = generateJWTNavIdent(
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
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
                            esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
                                moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
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
                            esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_INNKALT
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
                        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
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

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidSted))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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
                            esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_INNKALT
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
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

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDPostTidSted) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(newDialogmoteTidStedNoBehandler))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                            response.content!! shouldContain "Failed to change tid/sted: missing behandler"
                        }
                    }
                }
            }
        }
    }
})
