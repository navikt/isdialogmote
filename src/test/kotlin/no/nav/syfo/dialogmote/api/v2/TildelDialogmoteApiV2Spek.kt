package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.TildelDialogmoterDTO
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.database.getDialogmoteUnfinishedList
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class TildelDialogmoteApiV2Spek : Spek({
    describe(TildelDialogmoteApiV2Spek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database

        val esyfovarselHendelse = mockk<NarmesteLederHendelse>(relaxed = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

        val veilederCallerIdent = UserConstants.VEILEDER_IDENT
        val veilederIdentTildelesMoter = UserConstants.VEILEDER_IDENT_2
        val veilederCallerToken = generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            veilederCallerIdent,
        )

        beforeEachGroup {
            database.dropData()
        }
        beforeEachTest {
            val altinnResponse = ReceiptExternal()
            altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
            clearMocks(altinnMock)
            every {
                altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
            } returns altinnResponse
        }
        afterGroup { database.dropData() }

        describe("Tildel dialogmoter") {
            val urlMoterEnhet = "$dialogmoteApiV2Basepath$dialogmoteApiEnhetUrlPath/${UserConstants.ENHET_NR.value}"
            val urlTildelMote = "$dialogmoteApiV2Basepath$dialogmoteTildelPath"
            val newDialogmote = generateNewDialogmote(UserConstants.ARBEIDSTAKER_FNR)
            val newDialogmoteAnnenArbeidstaker = generateNewDialogmote(UserConstants.ARBEIDSTAKER_ANNEN_FNR)

            describe("Happy path") {
                it("should tildele dialogmoter if request is successful") {
                    val createdDialogmoterUuids = mutableListOf<UUID>()
                    database.connection.run { this.createNewDialogmoteWithReferences(newDialogmote) }
                    database.connection.run { this.createNewDialogmoteWithReferences(newDialogmoteAnnenArbeidstaker) }

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )

                        val response = client.get(urlMoterEnhet) {
                            bearerAuth(veilederCallerToken)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 2
                        dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederCallerIdent } shouldBeEqualTo true
                        dialogmoteList.any { dialogmoteDTO -> dialogmoteDTO.tildeltVeilederIdent == veilederIdentTildelesMoter } shouldBeEqualTo false

                        createdDialogmoterUuids.addAll(dialogmoteList.map { UUID.fromString(it.uuid) })

                        client.patch(urlTildelMote) {
                            bearerAuth(veilederCallerToken)
                            contentType(ContentType.Application.Json)
                            setBody(
                                TildelDialogmoterDTO(
                                    veilederIdent = veilederIdentTildelesMoter,
                                    dialogmoteUuids = createdDialogmoterUuids
                                )
                            )
                        }.apply {
                            response.status shouldBeEqualTo HttpStatusCode.OK
                        }
                    }

                    val dialogmoter = database.getDialogmoteUnfinishedList(EnhetNr(UserConstants.ENHET_NR.value))
                    dialogmoter.size shouldBeEqualTo 2
                    dialogmoter.all { dialogmote -> dialogmote.tildeltVeilederIdent == veilederIdentTildelesMoter } shouldBeEqualTo true
                    dialogmoter.all { dialogmote -> dialogmote.tildeltVeilederIdent == veilederCallerIdent } shouldBeEqualTo false
                }
            }

            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.patch(urlTildelMote)
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("should return status Bad Request if no dialogmoteUuids supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.patch(urlTildelMote) {
                            bearerAuth(veilederCallerToken)
                            contentType(ContentType.Application.Json)
                            setBody(
                                TildelDialogmoterDTO(
                                    veilederIdent = veilederIdentTildelesMoter,
                                    dialogmoteUuids = emptyList()
                                )
                            )
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status Forbidden if denied access to dialogmøte person") {
                    val createdDialogmoterUuids = mutableListOf<UUID>()

                    val newDialogmoteNoVeilederAccess =
                        generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                    database.connection.use { connection ->
                        val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmoteNoVeilederAccess
                        )
                        createdDialogmoterUuids.add(dialogmoteIdPair.second)
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.patch(urlTildelMote) {
                            bearerAuth(veilederCallerToken)
                            contentType(ContentType.Application.Json)
                            setBody(
                                TildelDialogmoterDTO(
                                    veilederIdent = veilederIdentTildelesMoter,
                                    dialogmoteUuids = createdDialogmoterUuids,
                                )
                            )
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("should return status Forbidden if contains dialogmøte with denied access to person") {
                    val createdDialogmoteUuid =
                        mutableListOf(database.connection.run { this.createNewDialogmoteWithReferences(newDialogmote) }.dialogmoteIdPair.second)

                    val newDialogmoteNoVeilederAccess =
                        generateNewDialogmote(UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                    database.connection.use { connection ->
                        val (dialogmoteIdPair) = connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmoteNoVeilederAccess
                        )
                        createdDialogmoteUuid.add(dialogmoteIdPair.second)
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.patch(urlTildelMote) {
                            bearerAuth(veilederCallerToken)
                            contentType(ContentType.Application.Json)
                            setBody(
                                TildelDialogmoterDTO(
                                    veilederIdent = veilederIdentTildelesMoter,
                                    dialogmoteUuids = createdDialogmoteUuid,
                                )
                            )
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
