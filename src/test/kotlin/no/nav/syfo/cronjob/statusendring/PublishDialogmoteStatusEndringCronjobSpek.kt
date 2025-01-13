package no.nav.syfo.cronjob.statusendring

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.dialogmote.api.v2.*
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PublishDialogmoteStatusEndringCronjobSpek : Spek({
    describe(PublishDialogmoteStatusEndringCronjobSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(dialogmelding = any()) }

        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        val dialogmoteStatusEndringProducer = mockk<DialogmoteStatusEndringProducer>()
        justRun { dialogmoteStatusEndringProducer.sendDialogmoteStatusEndring(any()) }

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        val behandlerVarselService = BehandlerVarselService(
            database = database,
            behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
        )

        val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
            database = database,
            dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
            moteStatusEndretRepository = moteStatusEndretRepository,
        )

        val publishDialogmoteStatusEndringCronjob = PublishDialogmoteStatusEndringCronjob(
            publishDialogmoteStatusEndringService = publishDialogmoteStatusEndringService,
        )

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
        }

        describe("Publish DialogmoteStatusEndring for types of StatusEndret") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                UserConstants.VEILEDER_IDENT,
            )

            it("should update publishedAt (ferdigstilt) without Behandler") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)

                testApplication {
                    val client = setupApiAndClient(
                        behandlerVarselService = behandlerVarselService,
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTO()

                    client.post(urlMoteUUIDPostTidSted) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(newDialogmoteTidSted)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val urlMoteUUIDFerdigstill =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                    val ferdigstillDialogMoteDto = generateNewReferatDTO()

                    client.post(urlMoteUUIDFerdigstill) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(ferdigstillDialogMoteDto)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                runBlocking {
                    val result = publishDialogmoteStatusEndringCronjob.dialogmoteStatusEndringPublishJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 3
                }
            }
            it("should update publishedAt (avlyst) with Behandler") {
                val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)

                testApplication {
                    val client = setupApiAndClient(
                        behandlerVarselService = behandlerVarselService,
                        altinnMock = altinnMock,
                        esyfovarselProducer = esyfovarselProducerMock
                    )
                    val createdDialogmoteUUID = client.postAndGetDialogmote(validToken, newDialogmoteDTO).uuid

                    val urlMoteUUIDPostTidSted =
                        "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteTidStedPath"
                    val newDialogmoteTidSted = generateEndreDialogmoteTidStedDTOWithBehandler()

                    client.post(urlMoteUUIDPostTidSted) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(newDialogmoteTidSted)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
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
                    }
                }

                runBlocking {
                    val dialogmoteStatusEndretList =
                        publishDialogmoteStatusEndringService.getDialogmoteStatuEndretToPublishList()
                    dialogmoteStatusEndretList.size shouldBeEqualTo 3

                    val dialogmoteStatusEndretListWithBehandler =
                        dialogmoteStatusEndretList.filter { dialogmoteStatusEndret ->
                            dialogmoteStatusEndret.motedeltakerBehandler
                        }
                    dialogmoteStatusEndretListWithBehandler.size shouldBeEqualTo 3

                    val result = publishDialogmoteStatusEndringCronjob.dialogmoteStatusEndringPublishJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 3
                }
            }
        }
    }
})
