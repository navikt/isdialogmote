package no.nav.syfo.brev.narmesteleder

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerResponsDTO
import no.nav.syfo.brev.domain.BrevType
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteEndreFerdigstiltPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR_2
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateInkallingHendelseOtherVirksomhet
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.testhelper.mock.pdfInnkalling
import no.nav.syfo.testhelper.mock.pdfReferat
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*

object NarmesteLederBrevSpek : Spek({
    describe(NarmesteLederBrevSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselProducer = mockk<EsyfovarselProducer>()

        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        val arbeidstakerVarselService = ArbeidstakerVarselService(
            esyfovarselProducer = esyfovarselProducer,
        )
        val dialogmotestatusService = DialogmotestatusService(
            oppfolgingstilfelleClient = externalMockEnvironment.oppfolgingstilfelleClient,
            moteStatusEndretRepository = MoteStatusEndretRepository(database),
        )
        val dialogmotedeltakerService = DialogmotedeltakerService(
            arbeidstakerVarselService = arbeidstakerVarselService,
            database = database,
        )
        val dialogmoterelasjonService = DialogmoterelasjonService(
            dialogmotedeltakerService = dialogmotedeltakerService,
            database = database,
        )

        beforeEachTest {
            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
            // Add dummy deltakere so that id for deltaker and mote does not match by accident
            database.addDummyDeltakere()
        }

        afterEachTest {
            database.dropData()
        }

        describe("Happy path") {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
            val newDialogmoteDTOOther = generateNewDialogmoteDTO(
                ARBEIDSTAKER_FNR,
                virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
            )
            val validTokenSelvbetjening = generateJWTTokenx(
                audience = externalMockEnvironment.environment.tokenxClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                pid = NARMESTELEDER_FNR.value,
            )
            val validTokenVeileder = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            val incorrectTokenSelvbetjening = generateJWTTokenx(
                audience = externalMockEnvironment.environment.tokenxClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                pid = NARMESTELEDER_FNR_2.value,
            )

            it("Should return OK when les and response") {

                testApplication {
                    val uuid: String
                    val createdDialogmoteUUID: String
                    val createdDialogmoteDeltakerArbeidsgiverUUID: String
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    client.postMote(validTokenVeileder, newDialogmoteDTO)
                    verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(generateInkallingHendelse()) }
                    clearMocks(esyfovarselProducerMock)

                    client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK

                        val dto = body<List<DialogmoteDTO>>().first()
                        dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        createdDialogmoteUUID = dto.uuid
                        createdDialogmoteDeltakerArbeidsgiverUUID = dto.arbeidsgiver.uuid
                    }

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        contentType(ContentType.Application.Json)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                        nlBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldBeNull()

                        uuid = narmesteLederBrevDTO.uuid
                    }

                    val urlNarmesteLederBrevUUIDLes =
                        "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiLesPath"

                    client.post(urlNarmesteLederBrevUUIDLes) {
                        bearerAuth(incorrectTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.Forbidden
                    }

                    client.post(urlNarmesteLederBrevUUIDLes) {
                        bearerAuth(validTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                        nlBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldNotBeNull()
                    }

                    val urlNarmesteLederBrevUUIDRespons =
                        "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiResponsPath"

                    client.post(urlNarmesteLederBrevUUIDRespons) {
                        bearerAuth(validTokenSelvbetjening)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArbeidstakerResponsDTO(
                                svarType = DialogmoteSvarType.KOMMER.name,
                                svarTekst = "Det passer bra - det/også <code>",
                            )
                        )
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val narmesteLederBrevList = body<List<NarmesteLederBrevDTO>>()
                        narmesteLederBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = narmesteLederBrevList.firstOrNull()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                    }

                    client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                        status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = body<List<DialogmoteDTO>>()
                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        dialogmoteDTO.arbeidsgiver.varselList[0].svar!!.svarTekst shouldBeEqualTo "Det passer bra - det/også _code_"
                    }

                    // Repeated invocation should fail
                    client.post(urlNarmesteLederBrevUUIDRespons) {
                        bearerAuth(validTokenSelvbetjening)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArbeidstakerResponsDTO(
                                svarType = DialogmoteSvarType.KOMMER.name,
                                svarTekst = "Det passer bra det fortsatt",
                            )
                        )
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.BadRequest
                    }

                    val pdfUrl = "$narmesteLederBrevApiBasePath/$uuid$narmesteLederBrevApiPdfPath"
                    client.get(pdfUrl) {
                        bearerAuth(validTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = bodyAsChannel().toByteArray()
                        pdfContent shouldBeEqualTo pdfInnkalling
                    }

                    val urlMoteUUIDReferat =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    client.post(urlMoteUUIDReferat) {
                        bearerAuth(validTokenVeileder)
                        contentType(ContentType.Application.Json)
                        setBody(generateNewReferatDTO())
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val createdReferatArbeidsgiverBrevUUID: String

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        val arbeidsgiverBrevDTO = arbeidsgiverBrevList.first()
                        arbeidsgiverBrevDTO.shouldNotBeNull()
                        arbeidsgiverBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidsgiverBrevDTO.lestDato.shouldBeNull()
                        arbeidsgiverBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        arbeidsgiverBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidsgiverUUID
                        arbeidsgiverBrevDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        val isCorrectDialogmotetid =
                            LocalDateTime.now().plusDays(29).isBefore(arbeidsgiverBrevDTO.tid)
                        isCorrectDialogmotetid shouldBeEqualTo true
                        createdReferatArbeidsgiverBrevUUID = arbeidsgiverBrevDTO.uuid
                    }

                    val urlReferatUUIDLes =
                        "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiLesPath"
                    client.post(urlReferatUUIDLes) {
                        bearerAuth(validTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val arbeidsgiverBrevDTO: NarmesteLederBrevDTO?
                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        arbeidsgiverBrevDTO = arbeidsgiverBrevList.firstOrNull()
                        arbeidsgiverBrevDTO.shouldNotBeNull()
                        arbeidsgiverBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidsgiverBrevDTO.lestDato.shouldNotBeNull()
                    }

                    client.post(urlReferatUUIDLes) {
                        bearerAuth(validTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                        arbeidsgiverBrevList.size shouldBeEqualTo 2

                        val arbeidstakerBrevUpdatedDTO = arbeidsgiverBrevList.firstOrNull()
                        arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                        arbeidstakerBrevUpdatedDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                        arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidsgiverBrevDTO!!.lestDato
                    }

                    val urlPdfForReferatNedlasting =
                        "$narmesteLederBrevApiBasePath/$createdReferatArbeidsgiverBrevUUID$narmesteLederBrevApiPdfPath"
                    client.get(urlPdfForReferatNedlasting) {
                        bearerAuth(validTokenSelvbetjening)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = bodyAsChannel().toByteArray()
                        pdfContent shouldBeEqualTo pdfReferat
                    }

                    val urlMoteUUIDEndreReferat =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteEndreFerdigstiltPath"
                    val referatEndretDto = generateNewReferatDTO(
                        behandlerOppgave = "Dette er en en endring",
                        begrunnelseEndring = "Dette er en begrunnelse",
                    )
                    client.post(urlMoteUUIDEndreReferat) {
                        bearerAuth(validTokenVeileder)
                        contentType(ContentType.Application.Json)
                        setBody(referatEndretDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                        val arbeidsgiverBrevList = body<List<NarmesteLederBrevDTO>>()
                        arbeidsgiverBrevList.size shouldBeEqualTo 3

                        val endretReferatBrevDTO = arbeidsgiverBrevList.firstOrNull()
                        endretReferatBrevDTO.shouldNotBeNull()
                        endretReferatBrevDTO.brevType shouldBeEqualTo BrevType.REFERAT_ENDRET.name
                    }
                }
            }
            it("Same narmesteleder and arbeidstaker, different virksomhet") {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    client.postMote(validTokenVeileder, newDialogmoteDTO)
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateInkallingHendelse()
                        )
                    }
                    clearMocks(esyfovarselProducerMock)

                    client.postMote(validTokenVeileder, newDialogmoteDTOOther)
                    verify(exactly = 1) {
                        esyfovarselProducerMock.sendVarselToEsyfovarsel(
                            generateInkallingHendelseOtherVirksomhet()
                        )
                    }
                    clearMocks(esyfovarselProducerMock)

                    client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        contentType(ContentType.Application.Json)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = body<List<NarmesteLederBrevDTO>>()
                        nlBrevList.size shouldBeEqualTo 2

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldBeNull()
                    }
                }
            }
            it("Return OK and empty brevlist when no brev exists") {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    val response = client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        contentType(ContentType.Application.Json)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val nlBrevList = response.body<List<NarmesteLederBrevDTO>>()
                    nlBrevList.size shouldBeEqualTo 0
                }
            }
            it("Return OK and empty brevlist when only brev from lukket mote exists") {
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                    val createdDialogmoteUUID: String = client.postAndGetDialogmote(validTokenVeileder, newDialogmoteDTO).uuid

                    val pMote = database.getDialogmote(UUID.fromString(createdDialogmoteUUID)).first()
                    val mote = dialogmoterelasjonService.extendDialogmoteRelations(pMote)

                    runBlocking {
                        database.connection.use { connection ->
                            dialogmotestatusService.updateMoteStatus(
                                connection = connection,
                                dialogmote = mote,
                                newDialogmoteStatus = DialogmoteStatus.LUKKET,
                                opprettetAv = "system",
                            )
                            connection.commit()
                        }
                    }

                    val response = client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        contentType(ContentType.Application.Json)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val nlBrevList = response.body<List<NarmesteLederBrevDTO>>()
                    nlBrevList.size shouldBeEqualTo 0
                }
            }
        }
        describe("Error handling") {
            it("Return BAD REQUEST when $NAV_PERSONIDENT_HEADER is missing") {
                val validTokenSelvbetjening = generateJWTTokenx(
                    audience = externalMockEnvironment.environment.tokenxClientId,
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    pid = NARMESTELEDER_FNR.value,
                )
                testApplication {
                    val client = setupApiAndClient(
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock,
                    )

                    val response = client.get(narmesteLederBrevApiBasePath) {
                        bearerAuth(validTokenSelvbetjening)
                        contentType(ContentType.Application.Json)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
        }
    }
})
