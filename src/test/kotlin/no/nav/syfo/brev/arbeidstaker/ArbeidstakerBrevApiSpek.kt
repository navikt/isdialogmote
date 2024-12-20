package no.nav.syfo.brev.arbeidstaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
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
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
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
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*

class ArbeidstakerBrevApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(ArbeidstakerBrevApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
            val esyfovarselHendelse = mockk<ArbeidstakerHendelse>(relaxed = true)
            justRun { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                altinnMock = altinnMock,
            )
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
                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

                    it("should return OK if request is successful") {
                        val createdArbeidstakerBrevUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducer)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar.shouldBeNull()
                            arbeidstakerBrevDTO!!.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            arbeidstakerBrevDTO!!.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            val isTodayBeforeDialogmotetid = LocalDateTime.now().isBefore(newDialogmoteDTO.tidSted.tid)
                            isTodayBeforeDialogmotetid shouldBeEqualTo true

                            clearMocks(esyfovarselProducer)
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }
                        val urlArbeidstakerBrevUUIDRespons =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDRespons) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        ArbeidstakerResponsDTO(
                                            svarType = DialogmoteSvarType.KOMMER.name,
                                            svarTekst = "Det passer bra();<code>, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?.",
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        }
                        with(
                            handleRequest(HttpMethod.Get, "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath") {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                            dialogmoteDTO.arbeidstaker.varselList[0].svar!!.svarTekst shouldBeEqualTo "Det passer bra();_code_, med nørskeÆØÅ bokstaver og noen spesialtegn %!()?."
                        }
                        // Repeated invocation should fail
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDRespons) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        ArbeidstakerResponsDTO(
                                            svarType = DialogmoteSvarType.KOMMER.name,
                                            svarTekst = "Det passer bra det fortsatt",
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
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
                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    val urlArbeidstakerMoterList = arbeidstakerBrevApiPath

                    it("should return OK if request is successful") {
                        val createdArbeidstakerBrevUUID: String

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.digitalt shouldBeEqualTo true
                            arbeidstakerBrevDTO.lestDato.shouldBeNull()

                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerBrevUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
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
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }
                        val urlArbeidstakerBrevUUIDRespons =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiResponsPath"

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDRespons) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(
                                    objectMapper.writeValueAsString(
                                        ArbeidstakerResponsDTO(
                                            svarType = DialogmoteSvarType.KOMMER.name,
                                            svarTekst = "Det passer bra",
                                        )
                                    )
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlArbeidstakerMoterList) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningOldFnr))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.svar!!.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        }
                        with(
                            handleRequest(HttpMethod.Get, "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath") {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FJERDE_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

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

                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID = ""
                    var createdDialogmoteDeltakerArbeidstakerUUID = ""

                    it("should return OK if request is successful") {
                        for (
                        dialogmoteDTO in listOf(
                            newDialogmoteLukket,
                            newDialogmoteAvlyst1,
                            newDialogmoteAvlyst2,
                            newDialogmoteInnkalt
                        )
                        ) {
                            with(
                                handleRequest(HttpMethod.Post, urlMote) {
                                    addHeader(Authorization, bearerHeader(validTokenVeileder))
                                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                    setBody(objectMapper.writeValueAsString(dialogmoteDTO))
                                }
                            ) {
                                response.status() shouldBeEqualTo HttpStatusCode.OK
                            }

                            with(
                                handleRequest(HttpMethod.Get, urlMote) {
                                    addHeader(Authorization, bearerHeader(validTokenVeileder))
                                    addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                                }
                            ) {
                                response.status() shouldBeEqualTo HttpStatusCode.OK

                                val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                                val dto = dialogmoteList.first()
                                dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                                createdDialogmoteUUID = dto.uuid
                                createdDialogmoteDeltakerArbeidstakerUUID = dto.arbeidstaker.uuid
                            }
                            if (dialogmoteDTO != newDialogmoteInnkalt && dialogmoteDTO != newDialogmoteLukket) {
                                val urlMoteUUIDAvlys =
                                    "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteAvlysPath"
                                val avlysDialogMoteDto = generateAvlysDialogmoteDTO()

                                with(
                                    handleRequest(HttpMethod.Post, urlMoteUUIDAvlys) {
                                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                        addHeader(Authorization, bearerHeader(validTokenVeileder))
                                        setBody(objectMapper.writeValueAsString(avlysDialogMoteDto))
                                    }
                                ) {
                                    response.status() shouldBeEqualTo HttpStatusCode.OK
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
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

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

                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerBrevUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
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
                        val referatDto = generateNewReferatDTO()
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDReferat) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                setBody(objectMapper.writeValueAsString(referatDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        val createdReferatArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
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
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        var arbeidstakerBrevDTO: ArbeidstakerBrevDTO?
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            arbeidstakerBrevDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO!!.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevDTO!!.lestDato.shouldNotBeNull()
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 6

                            val arbeidstakerBrevUpdatedDTO = arbeidstakerBrevList.firstOrNull()
                            arbeidstakerBrevUpdatedDTO.shouldNotBeNull()
                            arbeidstakerBrevUpdatedDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            arbeidstakerBrevUpdatedDTO.lestDato shouldBeEqualTo arbeidstakerBrevDTO!!.lestDato
                        }
                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo pdfInnkalling
                        }
                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo pdfReferat
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

                    val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    var createdDialogmoteUUID: String

                    it("should return Forbidden when bearer header contains token for unauthorized person") {
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteInnkalt))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlMote) {
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)
                            val dto = dialogmoteList.first()
                            dto.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                            createdDialogmoteUUID = dto.uuid
                        }

                        val createdArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)

                            arbeidstakerBrevList.size shouldBeEqualTo 1

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            createdArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }

                        val urlArbeidstakerVarselUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlArbeidstakerVarselUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 0
                        }

                        val urlMoteUUIDReferat =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        val referatDto = generateNewReferatDTO()
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDReferat) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(Authorization, bearerHeader(validTokenVeileder))
                                setBody(objectMapper.writeValueAsString(referatDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        val createdReferatArbeidstakerBrevUUID: String
                        with(
                            handleRequest(HttpMethod.Get, arbeidstakerBrevApiPath) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerBrevList =
                                objectMapper.readValue<List<ArbeidstakerBrevDTO>>(response.content!!)
                            arbeidstakerBrevList.size shouldBeEqualTo 2

                            val arbeidstakerBrevDTO = arbeidstakerBrevList.first()
                            arbeidstakerBrevDTO.shouldNotBeNull()
                            arbeidstakerBrevDTO.brevType shouldBeEqualTo MotedeltakerVarselType.REFERAT.name
                            createdReferatArbeidstakerBrevUUID = arbeidstakerBrevDTO.uuid
                        }
                        val urlReferatUUIDLes =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiLesPath"
                        with(
                            handleRequest(HttpMethod.Post, urlReferatUUIDLes) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }

                        val urlPdfForInnkallingNedlasting =
                            "$arbeidstakerBrevApiPath/$createdArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForInnkallingNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo pdfInnkalling
                        }

                        val urlPdfForReferatNedlasting =
                            "$arbeidstakerBrevApiPath/$createdReferatArbeidstakerBrevUUID$arbeidstakerBrevApiPdfPath"
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjeningAnnenPerson))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlPdfForReferatNedlasting) {
                                addHeader(Authorization, bearerHeader(validTokenSelvbetjening))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo pdfReferat
                        }
                    }
                }
            }
        }
    }
})
