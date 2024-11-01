package no.nav.syfo.janitor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.database.getDialogmoteList
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.janitor.kafka.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

fun generateJanitorEventDTO(action: String, referenceUuid: String): JanitorEventDTO = JanitorEventDTO(
    referenceUUID = referenceUuid,
    navident = UserConstants.VEILEDER_IDENT,
    eventUUID = UUID.randomUUID().toString(),
    personident = UserConstants.ARBEIDSTAKER_FNR.value,
    action = action,
)

val objectMapper: ObjectMapper = configuredJacksonMapper()

class JanitorServiceSpek : Spek({

    describe(JanitorService::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(any()) }
            val eventStatusProducerMock = mockk<JanitorEventStatusProducer>(relaxed = true)
            justRun { eventStatusProducerMock.sendEventStatus(any()) }

            val dialogmotestatusService = DialogmotestatusService(
                oppfolgingstilfelleClient = mockk(relaxed = true),
                moteStatusEndretRepository = MoteStatusEndretRepository(database),
            )
            val dialogmotedeltakerService =
                DialogmotedeltakerService(database = database, arbeidstakerVarselService = mockk())
            val dialogmoterelasjonService = DialogmoterelasjonService(
                database = database,
                dialogmotedeltakerService = dialogmotedeltakerService,
            )

            val janitorService = JanitorService(
                database = database,
                dialogmotestatusService = dialogmotestatusService,
                dialogmoterelasjonService = dialogmoterelasjonService,
                janitorEventStatusProducer = eventStatusProducerMock,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = mockk(),
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock,
            )

            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                clearMocks(altinnMock)
                clearMocks(eventStatusProducerMock)

                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            afterEachTest {
                database.dropData()
            }

            describe("Handles lukk møte event") {
                val validToken = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )
                val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                it("updates motestatus to LUKKET and produces event status OK") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val mote = database.getDialogmoteList(UserConstants.ARBEIDSTAKER_FNR).first()

                    val event = generateJanitorEventDTO(
                        action = JanitorAction.LUKK_DIALOGMOTE.name,
                        referenceUuid = mote.uuid.toString()
                    )
                    runBlocking { janitorService.handle(event) }

                    val motestatusList = database.getMoteStatusEndretNotPublished()
                    motestatusList.shouldNotBeEmpty()
                    val motestatus = motestatusList.last()
                    motestatus.status shouldBeEqualTo DialogmoteStatus.LUKKET
                    motestatus.opprettetAv shouldBeEqualTo UserConstants.VEILEDER_IDENT
                    motestatus.moteId shouldBeEqualTo mote.id

                    verify { eventStatusProducerMock.sendEventStatus(JanitorEventStatusDTO(eventUUID = event.eventUUID, status = JanitorEventStatus.OK)) }
                }

                it("produces event status FAILED if mote not found") {
                    val event = generateJanitorEventDTO(
                        action = JanitorAction.LUKK_DIALOGMOTE.name,
                        referenceUuid = UUID.randomUUID().toString()
                    )
                    runBlocking { janitorService.handle(event) }

                    verify { eventStatusProducerMock.sendEventStatus(JanitorEventStatusDTO(eventUUID = event.eventUUID, status = JanitorEventStatus.FAILED)) }
                }

                it("produces event status FAILED if mote finished") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val mote = database.getDialogmoteList(UserConstants.ARBEIDSTAKER_FNR).first()
                    val moteUuid = mote.uuid

                    with(
                        handleRequest(
                            HttpMethod.Post,
                            "$dialogmoteApiV2Basepath/${moteUuid}$dialogmoteApiMoteFerdigstillPath"
                        ) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(generateNewReferatDTO()))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val event = generateJanitorEventDTO(
                        action = JanitorAction.LUKK_DIALOGMOTE.name,
                        referenceUuid = moteUuid.toString()
                    )

                    runBlocking { janitorService.handle(event) }

                    verify { eventStatusProducerMock.sendEventStatus(JanitorEventStatusDTO(eventUUID = event.eventUUID, status = JanitorEventStatus.FAILED)) }
                }

                it("produces event status FAILED if mote on wrong person") {
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    )
                    with(
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    val mote = database.getDialogmoteList(UserConstants.ARBEIDSTAKER_FNR).first()
                    val event = generateJanitorEventDTO(
                        action = JanitorAction.LUKK_DIALOGMOTE.name,
                        referenceUuid = mote.uuid.toString()
                    ).copy(personident = UserConstants.ARBEIDSTAKER_ANNEN_FNR.value)

                    runBlocking { janitorService.handle(event) }

                    verify { eventStatusProducerMock.sendEventStatus(JanitorEventStatusDTO(eventUUID = event.eventUUID, status = JanitorEventStatus.FAILED)) }
                }
            }

            describe("Handles irrelevant event") {
                it("does not update mote status or produce event status") {
                    runBlocking {
                        janitorService.handle(
                            generateJanitorEventDTO(
                                "IRRELEVANT_ACTION",
                                UUID.randomUUID().toString()
                            )
                        )
                    }

                    database.getMoteStatusEndretNotPublished().shouldBeEmpty()
                    verify(exactly = 0) { eventStatusProducerMock.sendEventStatus(any()) }
                }
            }
        }
    }
})
