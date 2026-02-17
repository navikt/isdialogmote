package no.nav.syfo.dialogmote.api.v2.motestatusendring

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.api.dto.DialogmoteStatusEndringDTO
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.infrastructure.database.transaction
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_2
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import no.nav.syfo.testhelper.setupApiAndClient
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.domain.dialogmote.Dialogmote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class GetDialogmoteStatusEndringApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database
    private val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = mockk<OppfolgingstilfelleClient>(relaxed = true),
        moteStatusEndretRepository = MoteStatusEndretRepository(database),
    )

    private val newDialogmote = generateNewDialogmote(ARBEIDSTAKER_FNR)
    private val newDialogmoteFerdigstilt = generateNewDialogmote(
        personIdent = ARBEIDSTAKER_FNR,
        status = Dialogmote.Status.FERDIGSTILT,
    )

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        clearAllMocks()
    }

    @Test
    fun `returns no content when no mote`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
    }

    @Test
    fun `returns no content when no mote for given person`() {
        database.transaction {
            val createdDialogmoteIdentifiers = createNewDialogmoteWithReferences(newDialogmote)
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                tilfelleStart = null,
            )
        }

        testApplication {
            val client = setupApiAndClient()
            val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_ANNEN_FNR.value)
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
    }

    @Test
    fun `returns motestatusendringer for person`() {
        database.transaction {
            val createdDialogmoteIdentifiers = createNewDialogmoteWithReferences(newDialogmote)
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                tilfelleStart = null,
            )
        }

        testApplication {
            val client = setupApiAndClient()
            val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
            assertEquals(1, statusendringer.size)

            assertEquals(VEILEDER_IDENT, statusendringer[0].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.INNKALT, statusendringer[0].status)
            assertEquals(VEILEDER_IDENT, statusendringer[0].dialogmoteOpprettetAv)
        }
    }

    @Test
    fun `returns several motestatusendringer for one mote`() {
        database.transaction {
            val createdDialogmoteIdentifiers = createNewDialogmoteWithReferences(newDialogmote)
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                tilfelleStart = null,
            )
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = Dialogmote.Status.NYTT_TID_STED,
                opprettetAv = VEILEDER_IDENT_2,
                tilfelleStart = null,
            )
        }

        testApplication {
            val client = setupApiAndClient()
            val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
            assertEquals(2, statusendringer.size)

            assertEquals(VEILEDER_IDENT_2, statusendringer[0].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.NYTT_TID_STED, statusendringer[0].status)
            assertEquals(VEILEDER_IDENT, statusendringer[0].dialogmoteOpprettetAv)

            assertEquals(VEILEDER_IDENT, statusendringer[1].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.INNKALT, statusendringer[1].status)
            assertEquals(VEILEDER_IDENT, statusendringer[1].dialogmoteOpprettetAv)
        }
    }

    @Test
    fun `returns motestatusendringer for several moter`() {
        database.transaction {
            val moteId1 = createNewDialogmoteWithReferences(newDialogmote)
            val moteId2 = createNewDialogmoteWithReferences(newDialogmoteFerdigstilt)
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = moteId1.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                tilfelleStart = null,
            )
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmoteFerdigstilt,
                dialogmoteId = moteId2.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmoteFerdigstilt.status,
                opprettetAv = newDialogmoteFerdigstilt.opprettetAv,
                tilfelleStart = null,
            )
            dialogmotestatusService.createMoteStatusEndring(
                unitOfWork = this,
                newDialogmote = newDialogmote,
                dialogmoteId = moteId1.dialogmoteIdPair.first,
                dialogmoteStatus = Dialogmote.Status.AVLYST,
                opprettetAv = VEILEDER_IDENT_2,
                tilfelleStart = null,
            )
        }

        testApplication {
            val client = setupApiAndClient()
            val response = client.get("$dialogmoteApiV2Basepath/personident/motestatusendringer") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val statusendringer = response.body<List<DialogmoteStatusEndringDTO>>()
            assertEquals(3, statusendringer.size)

            assertEquals(VEILEDER_IDENT_2, statusendringer[0].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.AVLYST, statusendringer[0].status)
            assertEquals(VEILEDER_IDENT, statusendringer[0].dialogmoteOpprettetAv)
            assertEquals(statusendringer[2].dialogmoteId, statusendringer[0].dialogmoteId)

            assertEquals(VEILEDER_IDENT, statusendringer[1].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.FERDIGSTILT, statusendringer[1].status)
            assertEquals(VEILEDER_IDENT, statusendringer[1].dialogmoteOpprettetAv)

            assertEquals(VEILEDER_IDENT, statusendringer[2].statusEndringOpprettetAv)
            assertEquals(Dialogmote.Status.INNKALT, statusendringer[2].status)
            assertEquals(VEILEDER_IDENT, statusendringer[2].dialogmoteOpprettetAv)
        }
    }
}
