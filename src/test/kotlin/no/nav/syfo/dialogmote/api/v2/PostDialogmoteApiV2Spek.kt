package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
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
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class PostDialogmoteApiV2Spek : Spek({
    describe(PostDialogmoteApiV2Spek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val esyfovarselHendelse = generateInkallingHendelse()
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )

        beforeGroup {
            database.dropData()
        }

        describe("Create Dialogmote for PersonIdent payload") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )

            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(altinnMock)
                clearMocks(esyfovarselProducerMock)
                justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            afterEachTest {
                database.dropData()
            }

            describe("Happy path") {
                it("should return OK if request is successful") {
                    val moteTidspunkt = DIALOGMOTE_TIDSPUNKT_FIXTURE
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = ARBEIDSTAKER_FNR,
                        dato = moteTidspunkt,
                    )

                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        verify(exactly = 1) {
                            altinnMock.insertCorrespondenceBasicV2(
                                any(),
                                any(),
                                any(),
                                any(),
                                any()
                            )
                        }

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                        dialogmoteDTO.behandler shouldBeEqualTo null

                        val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                        arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                        arbeidstakerVarselDTO.lestDato.shouldBeNull()
                        arbeidstakerVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidstaker"

                        arbeidstakerVarselDTO.document.size shouldBeEqualTo 5
                        arbeidstakerVarselDTO.document[0].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        arbeidstakerVarselDTO.document[0].title shouldBeEqualTo "Tittel innkalling"
                        arbeidstakerVarselDTO.document[0].texts shouldBeEqualTo emptyList()
                        arbeidstakerVarselDTO.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        arbeidstakerVarselDTO.document[1].title shouldBeEqualTo "Møtetid:"
                        arbeidstakerVarselDTO.document[1].texts shouldBeEqualTo listOf("5. mai 2021")
                        arbeidstakerVarselDTO.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        arbeidstakerVarselDTO.document[2].texts shouldBeEqualTo listOf("Brødtekst")
                        arbeidstakerVarselDTO.document[3].type shouldBeEqualTo DocumentComponentType.LINK
                        arbeidstakerVarselDTO.document[3].texts shouldBeEqualTo listOf("https://nav.no/")
                        arbeidstakerVarselDTO.document[4].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                        arbeidstakerVarselDTO.document[4].texts shouldBeEqualTo listOf(
                            "Vennlig hilsen",
                            "NAV Staden",
                            "Kari Saksbehandler"
                        )

                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.arbeidsgiver.varselList.size shouldBeEqualTo 1
                        val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                        arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                        arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidsgiver"

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"

                        val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                        moteStatusEndretList.size shouldBeEqualTo 1

                        moteStatusEndretList.first().status.name shouldBeEqualTo dialogmoteDTO.status
                        moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                        moteStatusEndretList.first().tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                    }
                }

                it("should return OK if request is successful: optional values missing") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                        dialogmoteDTO.behandler shouldBeEqualTo null

                        val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                        arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                        arbeidstakerVarselDTO.lestDato.shouldBeNull()
                        arbeidstakerVarselDTO.fritekst shouldBeEqualTo ""

                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo ""
                    }
                }
                it("should return OK if request is successful: with behandler") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()
                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.behandler shouldNotBeEqualTo null
                        dialogmoteDTO.behandler!!.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                        dialogmoteDTO.behandler!!.behandlerNavn shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerNavn
                        dialogmoteDTO.behandler!!.behandlerKontor shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerKontor
                        dialogmoteDTO.behandler!!.personIdent shouldBeEqualTo newDialogmoteDTO.behandler!!.personIdent

                        val behandlerVarselDTO = dialogmoteDTO.behandler!!.varselList.first()
                        behandlerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        behandlerVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum behandler"

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo ""

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
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newDialogmoteDTO.behandler!!.innkalling.serialize()
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.DIALOGMOTE.name
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.INNKALLING.value
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo null
                        kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        kafkaBehandlerDialogmeldingDTO.kilde shouldBeEqualTo "SYFO"
                    }
                }

                it("should return OK if request is successful: does not have a leader for Virksomhet") {
                    val newDialogmoteDTO =
                        generateNewDialogmoteDTO(personIdent = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                        verify(exactly = 1) {
                            altinnMock.insertCorrespondenceBasicV2(
                                any(),
                                any(),
                                any(),
                                any(),
                                any()
                            )
                        }
                    }
                }
                it("should return OK if requesting to create Dialogmote for PersonIdent with inactive Oppfolgingstilfelle") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE)
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                }

                it("return OK when creating dialogmote for innbygger without Oppfolgingstilfelle") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE)
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                    val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                    moteStatusEndretList.size shouldBeEqualTo 1

                    moteStatusEndretList.first().status.name shouldBeEqualTo DialogmoteStatus.INNKALT.name
                    moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                    moteStatusEndretList.first().tilfelleStart shouldBeEqualTo LocalDate.EPOCH
                }
            }

            describe("Unhappy paths") {
                val url = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.post(url)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                }

                it("should return status Forbidden if denied access to person") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                    val urlMotePersonIdent = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.post(urlMotePersonIdent) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(newDialogmoteDTO)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                }

                it("should return Conflict if requesting to create Dialogmote for PersonIdent with an existing unfinished Dialogmote") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )

                        client.postMote(validToken, newDialogmoteDTO).apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }

                        val response = client.postMote(validToken, newDialogmoteDTO)
                        response.status shouldBeEqualTo HttpStatusCode.Conflict
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                }

                it("should return InternalServerError if requesting to create Dialogmote for PersonIdent no behandlende enhet") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_BEHANDLENDE_ENHET)
                    testApplication {
                        val client = setupApiAndClient(
                            behandlerVarselService = behandlerVarselService,
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        val response = client.postMote(validToken, newDialogmoteDTO)
                        response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducerMock)
                    }
                }
            }
        }
    }
})
