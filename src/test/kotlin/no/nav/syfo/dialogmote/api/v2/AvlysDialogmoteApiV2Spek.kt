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
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class AvlysDialogmoteApiV2Spek : Spek({
    describe(AvlysDialogmoteApiV2Spek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
        val altinnResponse = ReceiptExternal()
        altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

        beforeEachTest {
            clearMocks(behandlerDialogmeldingProducer)
            justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
            clearMocks(esyfovarselProducerMock)
            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
        }

        afterEachTest {
            database.dropData()
        }

        describe("Avlys Dialogmote") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                it("should return OK if request is successful") {
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(avlysDialogMoteDto)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) {
                                esyfovarselProducerMock.sendVarselToEsyfovarsel(
                                    generateAvlysningHendelse()
                                )
                            }
                        }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.AVLYST.name

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                                it.varselType == MotedeltakerVarselType.AVLYST.name
                            }
                            arbeidstakerVarselDTO.shouldNotBeNull()
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo avlysDialogMoteDto.arbeidstaker.begrunnelse

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                                it.varselType == MotedeltakerVarselType.AVLYST.name
                            }
                            arbeidsgiverVarselDTO.shouldNotBeNull()
                            arbeidsgiverVarselDTO.fritekst shouldBeEqualTo avlysDialogMoteDto.arbeidsgiver.begrunnelse

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid =
                                LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            verify(exactly = 0) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                            val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 2

                            moteStatusEndretList.forEach { moteStatusEndret ->
                                moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                                moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                            }
                        }

                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(avlysDialogMoteDto)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to Avlys Dialogmote: already Avlyst"
                        }

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        val newReferatDTO = generateNewReferatDTO()

                        client.post(urlMoteUUIDFerdigstill) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(newReferatDTO)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to Ferdigstille Dialogmote, already Avlyst"
                        }

                        val urlMoteUUIDPostTidSted =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                        val endreTidStedDialogMoteDto = generateEndreDialogmoteTidStedDTO()

                        client.post(urlMoteUUIDPostTidSted) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(endreTidStedDialogMoteDto)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Conflict
                            bodyAsText() shouldContain "Failed to change tid/sted, already Avlyst"
                        }
                    }
                }
            }
            describe("With behandler") {
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)

                it("should return OK if request is successful") {

                    testApplication {
                        val createdDialogmoteUUID: String
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                        verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        clearMocks(behandlerDialogmeldingProducer)
                        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(avlysDialogMoteDto)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) {
                                esyfovarselProducerMock.sendVarselToEsyfovarsel(
                                    generateAvlysningHendelse()
                                )
                            }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.AVLYST.name

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
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo avlysDialogMoteDto.behandler!!.avlysning.serialize()
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.AVLYST.value
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldNotBeEqualTo null
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                    }
                }
                it("should throw exception if mote with behandler and avlysning missing behandler") {

                    testApplication {
                        val createdDialogmoteUUID: String
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                        verify(exactly = 1) { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                        clearMocks(behandlerDialogmeldingProducer)
                        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTONoBehandler()

                        val response = client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(avlysDialogMoteDto)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                        response.bodyAsText() shouldContain "Failed to Avlys Dialogmote: missing behandler"
                    }
                }
            }

            describe("Møtet tilbake i tid") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(
                    personIdent = ARBEIDSTAKER_FNR,
                    dato = LocalDateTime.now().plusDays(-30)
                )

                it("should return OK if request is successful") {
                    testApplication {
                        val createdDialogmoteUUID: String
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) } // INNKALT

                        client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDAvlys =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                        val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                        client.post(urlMoteUUIDAvlys) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(avlysDialogMoteDto)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) {
                                esyfovarselProducerMock.sendVarselToEsyfovarsel(
                                    generateAvlysningHendelse()
                                )
                            }
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.AVLYST.name

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.find {
                            it.varselType == MotedeltakerVarselType.AVLYST.name
                        }
                        arbeidstakerVarselDTO.shouldNotBeNull()
                        arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                        arbeidstakerVarselDTO.lestDato.shouldBeNull()
                        arbeidstakerVarselDTO.fritekst shouldBeEqualTo avlysDialogMoteDto.arbeidstaker.begrunnelse

                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.find {
                            it.varselType == MotedeltakerVarselType.AVLYST.name
                        }
                        arbeidsgiverVarselDTO.shouldNotBeNull()
                        arbeidsgiverVarselDTO.fritekst shouldBeEqualTo avlysDialogMoteDto.arbeidsgiver.begrunnelse

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        val isTodayBeforeDialogmotetid =
                            LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                        isTodayBeforeDialogmotetid shouldBeEqualTo false

                        val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                        moteStatusEndretList.size shouldBeEqualTo 2

                        moteStatusEndretList.forEach { moteStatusEndret ->
                            moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                            moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                        }
                    }
                }
            }
        }
    }
})
