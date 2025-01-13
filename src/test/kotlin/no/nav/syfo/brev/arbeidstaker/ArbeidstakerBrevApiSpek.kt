package no.nav.syfo.brev.arbeidstaker

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerBrevDTO
import no.nav.syfo.brev.arbeidstaker.domain.ArbeidstakerResponsDTO
import no.nav.syfo.brev.esyfovarsel.ArbeidstakerHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteAvlysPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FJERDE_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_TREDJE_FNR
import no.nav.syfo.testhelper.generator.generateAvlysDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.testhelper.mock.pdfInnkalling
import no.nav.syfo.testhelper.mock.pdfReferat
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*

class ArbeidstakerBrevApiSpek : Spek({
    describe(ArbeidstakerBrevApiSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
        val esyfovarselHendelse = mockk<ArbeidstakerHendelse>(relaxed = true)
        justRun { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }

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

            justRun { esyfovarselProducer.sendVarselToEsyfovarsel(any()) }
            // Add dummy deltakere so that id for deltaker and mote does not match by accident
            database.addDummyDeltakere()
        }

        afterEachTest {
            database.dropData()
        }

        describe("Les og respons ArbeidstakerBrev") {
            val validTokenSelvbetjening = generateJWTTokenx(
                audience = externalMockEnvironment.environment.tokenxClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                pid = ARBEIDSTAKER_FNR.value,
            )
            val validTokenVeileder = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                UserConstants.VEILEDER_IDENT,
            )
            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
                val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

                it("should return OK if request is successful") {

                    testApplication {
                        val createdArbeidstakerBrevUUID: String
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        client.postMote(validTokenVeileder, newDialogmoteDTO)
                        verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        clearMocks(esyfovarselProducer)

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                        client.post(urlArbeidstakerBrevUUIDLes) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar.shouldBeNull()
                            arbeidstakerBrevDTO!!.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO!!.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid =
                                LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            clearMocks(esyfovarselProducer)
                        }

                        client.post(urlArbeidstakerBrevUUIDLes) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }

                        val urlArbeidstakerBrevUUIDRespons =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"
                        client.post(urlArbeidstakerBrevUUIDRespons) {
                            bearerAuth(validTokenSelvbetjening)
                            contentType(ContentType.Application.Json)
                            setBody(
                                ArbeidstakerResponsDTO(
                                    svarType = DialogmoteSvarType.KOMMER.name,
                                    svarTekst = "Det passer bra();<code>, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?.",
                                )
                            )
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        }

                        client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = body<List<DialogmoteDTO>>()
                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                            dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarTekst shouldBeEqualTo "Det passer bra();_code_, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?."
                        }

                        // Repeated invocation should fail
                        val response = client.post(urlArbeidstakerBrevUUIDRespons) {
                            bearerAuth(validTokenSelvbetjening)
                            contentType(ContentType.Application.Json)
                            setBody(
                                ArbeidstakerResponsDTO(
                                    svarType = DialogmoteSvarType.KOMMER.name,
                                    svarTekst = "Det passer bra det fortsatt",
                                )
                            )
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
            describe("Happy path for arbeidstaker som har byttet fnr") {
                val validTokenSelvbetjeningOldFnr = generateJWTTokenx(
                    audience = externalMockEnvironment.environment.tokenxClientId,
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    pid = ARBEIDSTAKER_TREDJE_FNR.value,
                )
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FJERDE_FNR)
                val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

                it("should return OK if request is successful") {
                    testApplication {
                        val createdArbeidstakerBrevUUID: String
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        client.postMote(validTokenVeileder, newDialogmoteDTO)

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        client.post(urlArbeidstakerBrevUUIDLes) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar.shouldBeNull()
                            arbeidstakerBrevDTO!!.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO!!.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted

                            clearMocks(esyfovarselProducer)
                        }

                        client.post(urlArbeidstakerBrevUUIDLes) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }

                        val urlArbeidstakerBrevUUIDRespons =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"
                        client.post(urlArbeidstakerBrevUUIDRespons) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                            contentType(ContentType.Application.Json)
                            setBody(
                                ArbeidstakerResponsDTO(
                                    svarType = DialogmoteSvarType.KOMMER.name,
                                    svarTekst = "Det passer bra",
                                )
                            )
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(urlArbeidstakerMoterList) {
                            bearerAuth(validTokenSelvbetjeningOldFnr)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        }

                        val response = client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FJERDE_FNR)
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarTekst shouldBeEqualTo "Det passer bra"
                    }
                }
            }
            describe("Happy path med mer enn et møte for aktuell person") {
                val newDialogmoteLukket =
                    generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 0", LocalDateTime.now().plusDays(5))
                val newDialogmoteAvlyst1 =
                    generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 1", LocalDateTime.now().plusDays(10))
                val newDialogmoteAvlyst2 =
                    generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 2", LocalDateTime.now().plusDays(20))
                val newDialogmoteInnkalt =
                    generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted 3", LocalDateTime.now().plusDays(30))
                val dialogmoteDTOList = listOf(
                    newDialogmoteLukket,
                    newDialogmoteAvlyst1,
                    newDialogmoteAvlyst2,
                    newDialogmoteInnkalt
                )
                it("should return OK if request is successful") {
                    testApplication {
                        var createdDialogmoteUUID = ""
                        var createdDialogmoteDeltakerArbeidstakerUUID = ""
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )

                        for (dialogmoteDTO in dialogmoteDTOList) {
                            client.postMote(validTokenVeileder, dialogmoteDTO)

                            client.getDialogmoter(validTokenVeileder, ARBEIDSTAKER_FNR).apply {
                                status shouldBeEqualTo HttpStatusCode.OK
                                val dialogmoteList = body<List<DialogmoteDTO>>()
                                val dto = dialogmoteList.first()
                                dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                                createdDialogmoteUUID = dto.uuid
                                createdDialogmoteDeltakerArbeidstakerUUID = dto.arbeidstaker.uuid
                            }

                            if (dialogmoteDTO != newDialogmoteInnkalt && dialogmoteDTO != newDialogmoteLukket) {
                                val urlMoteUUIDAvlys =
                                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"

                                client.post(urlMoteUUIDAvlys) {
                                    bearerAuth(validTokenVeileder)
                                    contentType(ContentType.Application.Json)
                                    setBody(generateAvlysDialogmoteDTO())
                                }.apply {
                                    status shouldBeEqualTo HttpStatusCode.OK
                                }
                            }
                            if (dialogmoteDTO == newDialogmoteLukket) {
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
                            }
                        }

                        val createdArbeidstakerBrevUUID: String

                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 5

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        client.post(urlArbeidstakerBrevUUIDLes) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 5

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID

                            arbeidstakerBrevDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid =
                                LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
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

                        val createdReferatArbeidstakerBrevUUID: String
                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()
                            arbeidstakerBrevDTO.virksomhetsnummer shouldBeEqualTo newDialogmoteInnkalt.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO.deltakerUuid shouldBeEqualTo createdDialogmoteDeltakerArbeidstakerUUID
                            arbeidstakerBrevDTO.sted shouldBeEqualTo newDialogmoteInnkalt.tidSted.sted
                            val isCorrectDialogmotetid =
                                LocalDateTime.now().plusDays(29).isBefore(arbeidstakerBrevDTO.tid)
                            isCorrectDialogmotetid shouldBeEqualTo true
                            createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlReferatUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        client.post(urlReferatUUIDLes) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                        }

                        client.post(urlReferatUUIDLes) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                        }

                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                            arbeidstakerBrevUpdatedDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }

                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        client.get(urlPdfForInnkallingNedlasting) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = bodyAsChannel().toByteArray()
                            pdfContent shouldBeEqualTo pdfInnkalling
                        }

                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        client.get(urlPdfForReferatNedlasting) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = bodyAsChannel().toByteArray()
                            pdfContent shouldBeEqualTo pdfReferat
                        }
                    }
                }
            }
            describe("Uautorisert person nektes tilgang") {
                val newDialogmoteInnkalt =
                    generateNewDialogmoteDTO(ARBEIDSTAKER_FNR, "Sted", LocalDateTime.now().plusDays(30))

                val validTokenSelvbetjeningAnnenPerson = generateJWTTokenx(
                    audience = externalMockEnvironment.environment.tokenxClientId,
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    pid = ARBEIDSTAKER_ANNEN_FNR.value,
                )

                it("should return Forbidden when bearer header contains token for unauthorized person") {
                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                        )
                        val createdDialogmoteUUID = client.postAndGetDialogmote(validTokenVeileder, newDialogmoteInnkalt).uuid
                        val createdArbeidstakerBrevUUID: String

                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerVarselUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        client.post(urlArbeidstakerVarselUUIDLes) {
                            bearerAuth(validTokenSelvbetjeningAnnenPerson)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjeningAnnenPerson)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()
                            arbeidstakerBrevList.size shouldBeEqualTo 0
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

                        val createdReferatArbeidstakerBrevUUID: String
                        client.get(arbeidstakerBrevApiPath) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList = body<List<ArbeidstakerBrevDTO>>()

                            arbeidstakerBrevList.size shouldBeEqualTo 2

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlReferatUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        client.post(urlReferatUUIDLes) {
                            bearerAuth(validTokenSelvbetjeningAnnenPerson)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        client.get(urlPdfForInnkallingNedlasting) {
                            bearerAuth(validTokenSelvbetjeningAnnenPerson)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        client.get(urlPdfForInnkallingNedlasting) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = bodyAsChannel().toByteArray()
                            pdfContent shouldBeEqualTo pdfInnkalling
                        }

                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        client.get(urlPdfForReferatNedlasting) {
                            bearerAuth(validTokenSelvbetjeningAnnenPerson)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        client.get(urlPdfForReferatNedlasting) {
                            bearerAuth(validTokenSelvbetjening)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = bodyAsChannel().toByteArray()
                            pdfContent shouldBeEqualTo pdfReferat
                        }
                    }
                }
            }
        }
    }
})
