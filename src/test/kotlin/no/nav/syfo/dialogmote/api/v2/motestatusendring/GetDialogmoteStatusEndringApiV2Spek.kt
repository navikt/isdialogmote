package no.nav.syfo.dialogmote.api.v2.motestatusendring

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.api.domain.DialogmoteStatusEndringDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.setupApiAndClient
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GetDialogmoteStatusEndringApiV2Spek : Spek({
    describe("DialogmoteApiSpek") {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val dialogmotestatusService = DialogmotestatusService(
            oppfolgingstilfelleClient = mockk<OppfolgingstilfelleClient>(relaxed = true),
            moteStatusEndretRepository = MoteStatusEndretRepository(database),
        )

        describe("Get motestatusendringer") {
            val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_FNR)
            val newDialogmoteFerdigstilt = generateNewDialogmote(
                personIdent = ARBEIDSTAKER_FNR,
                status = DialogmoteStatus.FERDIGSTILT,
            )

            afterEachTest {
                database.dropData()
            }

            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )

            it("returns no content when no mote") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.NoContent
                }
            }

            it("returns no content when no mote for given person") {
                runBlocking {
                    database.connection.use { connection ->
                        val createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(newDialogmote)
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                            dialogmoteStatus = newDialogmote.status,
                            opprettetAv = newDialogmote.opprettetAv,
                        )
                        connection.commit()
                    }
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_ANNEN_FNR.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.NoContent
                }
            }

            it("returns motestatusendringer for person") {
                runBlocking {
                    database.connection.use { connection ->
                        val createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(newDialogmote)
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                            dialogmoteStatus = newDialogmote.status,
                            opprettetAv = newDialogmote.opprettetAv,
                        )
                        connection.commit()
                    }
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
                    statusendringer.size shouldBeEqualTo 1

                    statusendringer[0].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                    statusendringer[0].status shouldBeEqualTo DialogmoteStatus.INNKALT
                    statusendringer[0].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                }
            }

            it("returns several motestatusendringer for one mote") {
                runBlocking {
                    database.connection.use { connection ->
                        val createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(newDialogmote)
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                            dialogmoteStatus = newDialogmote.status,
                            opprettetAv = newDialogmote.opprettetAv,
                        )
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                            dialogmoteStatus = DialogmoteStatus.NYTT_TID_STED,
                            opprettetAv = VEILEDER_IDENT_2,
                        )
                        connection.commit()
                    }
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
                    statusendringer.size shouldBeEqualTo 2

                    statusendringer[0].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT_2
                    statusendringer[0].status shouldBeEqualTo DialogmoteStatus.NYTT_TID_STED
                    statusendringer[0].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT

                    statusendringer[1].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                    statusendringer[1].status shouldBeEqualTo DialogmoteStatus.INNKALT
                    statusendringer[1].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                }
            }

            it("returns motestatusendringer for several moter") {
                runBlocking {
                    database.connection.use { connection ->
                        val moteId1 = connection.createNewDialogmoteWithReferences(newDialogmote)
                        val moteId2 = connection.createNewDialogmoteWithReferences(newDialogmoteFerdigstilt)
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = moteId1.dialogmoteIdPair.first,
                            dialogmoteStatus = newDialogmote.status,
                            opprettetAv = newDialogmote.opprettetAv,
                        )
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmoteFerdigstilt,
                            dialogmoteId = moteId2.dialogmoteIdPair.first,
                            dialogmoteStatus = newDialogmoteFerdigstilt.status,
                            opprettetAv = newDialogmoteFerdigstilt.opprettetAv,
                        )
                        dialogmotestatusService.createMoteStatusEndring(
                            connection = connection,
                            newDialogmote = newDialogmote,
                            dialogmoteId = moteId1.dialogmoteIdPair.first,
                            dialogmoteStatus = DialogmoteStatus.AVLYST,
                            opprettetAv = VEILEDER_IDENT_2,
                        )

                        connection.commit()
                    }
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
                    statusendringer.size shouldBeEqualTo 3

                    statusendringer[0].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT_2
                    statusendringer[0].status shouldBeEqualTo DialogmoteStatus.AVLYST
                    statusendringer[0].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                    statusendringer[0].dialogmoteId shouldBeEqualTo statusendringer[2].dialogmoteId

                    statusendringer[1].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                    statusendringer[1].status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT
                    statusendringer[1].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT

                    statusendringer[2].statusEndringOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                    statusendringer[2].status shouldBeEqualTo DialogmoteStatus.INNKALT
                    statusendringer[2].dialogmoteOpprettetAv shouldBeEqualTo VEILEDER_IDENT
                }
            }
        }
    }
})
