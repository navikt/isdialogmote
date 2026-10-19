package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.dto.EndreDialogmoteDTO
import no.nav.syfo.api.dto.EndreMoteBegrunnelseBehandlerDTO
import no.nav.syfo.api.dto.EndreMoteBegrunnelseDTO
import no.nav.syfo.api.dto.EndreMoteNyBehandlerDTO
import no.nav.syfo.api.dto.NewDialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteEndrePath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class PostDialogmoteEndreApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
    private val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    private val dialogmeldingClient = mockk<DialogmeldingClient>(relaxed = true)
    private val behandlerVarselService = BehandlerVarselService(
        database = database,
        dialogmeldingClient = dialogmeldingClient,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )

    private val validToken = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )

    private val nyBehandlerRef = UUID.randomUUID().toString()
    private val sentDialogmeldinger = mutableListOf<KafkaBehandlerDialogmeldingDTO>()

    @BeforeEach
    fun setup() {
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(any()) }
        every { behandlerDialogmeldingProducer.sendDialogmelding(capture(sentDialogmeldinger)) } just Runs
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearAllMocks()
        sentDialogmeldinger.clear()
    }

    private fun document(text: String) = listOf(
        DocumentComponentDTO(type = DocumentComponentType.PARAGRAPH, title = null, texts = listOf(text))
    )

    private fun begrunnelse(text: String) = EndreMoteBegrunnelseDTO(text, document(text))

    private fun endreDTO(
        newDialogmoteDTO: NewDialogmoteDTO,
        behandler: EndreMoteBegrunnelseBehandlerDTO?,
        tidStedChanged: Boolean,
    ) = EndreDialogmoteDTO(
        sted = if (tidStedChanged) "Et annet sted" else newDialogmoteDTO.tidSted.sted,
        tid = newDialogmoteDTO.tidSted.tid,
        videoLink = newDialogmoteDTO.tidSted.videoLink,
        arbeidstaker = begrunnelse("arbeidstaker"),
        arbeidsgiver = begrunnelse("arbeidsgiver"),
        behandler = behandler,
    )

    private fun leggTilBehandler() = EndreMoteBegrunnelseBehandlerDTO(
        fritekst = "innkalling behandler",
        dokument = document("innkalling behandler"),
        deltaker = EndreMoteNyBehandlerDTO(
            personIdent = null,
            behandlerRef = nyBehandlerRef,
            behandlerNavn = "Ny Lege",
            behandlerKontor = "Nytt Kontor",
        ),
    )

    private fun endringBehandler() = EndreMoteBegrunnelseBehandlerDTO(
        fritekst = "endring behandler",
        dokument = document("endring behandler"),
        deltaker = null,
    )

    private suspend fun HttpClient.createMote(newDialogmoteDTO: NewDialogmoteDTO): String {
        postMote(validToken, newDialogmoteDTO)
        sentDialogmeldinger.clear()
        clearMocks(esyfovarselProducerMock, answers = false)
        return getMote().uuid
    }

    private suspend fun HttpClient.getMote(): DialogmoteDTO =
        getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first()

    private suspend fun HttpClient.endre(moteUuid: String, dto: EndreDialogmoteDTO): HttpResponse =
        post("$dialogmoteApiV2Basepath/$moteUuid$dialogmoteApiMoteEndrePath") {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

    private fun ApplicationTestBuilder.client() = setupApiAndClient(
        behandlerVarselService = behandlerVarselService,
        esyfovarselProducer = esyfovarselProducerMock,
    )

    @Test
    fun `adds behandler to mote without behandler`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, leggTilBehandler(), tidStedChanged = false))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(MoteEndringType.BEHANDLER_LAGT_TIL), endringer(moteUuid))

        val mote = client.getMote()
        assertEquals(Dialogmote.Status.ENDRET.name, mote.status)
        assertEquals(nyBehandlerRef, mote.behandler?.behandlerRef)
        assertEquals(listOf(MotedeltakerVarselType.INNKALT.name), mote.behandler?.varselList?.map { it.varselType })
        assertEquals(listOf("innkalling behandler"), mote.behandler?.varselList?.first()?.document?.first()?.texts)
        assertTrue(mote.arbeidstaker.varselList.any { it.varselType == MotedeltakerVarselType.ENDRET.name })
        assertTrue(mote.arbeidsgiver.varselList.any { it.varselType == MotedeltakerVarselType.ENDRET.name })

        assertEquals(1, sentDialogmeldinger.size)
        val dialogmelding = sentDialogmeldinger.first()
        assertEquals(nyBehandlerRef, dialogmelding.behandlerRef)
        assertEquals(DialogmeldingKode.INNKALLING.value, dialogmelding.dialogmeldingKode)
        assertNull(dialogmelding.dialogmeldingRefParent)
        assertEquals(dialogmelding.dialogmeldingUuid, dialogmelding.dialogmeldingRefConversation)
    }

    @Test
    fun `adds behandler and changes tid sted`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, leggTilBehandler(), tidStedChanged = true))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            listOf(MoteEndringType.TID_STED, MoteEndringType.BEHANDLER_LAGT_TIL),
            endringer(moteUuid),
        )
        val mote = client.getMote()
        assertEquals(1, mote.arbeidstaker.varselList.count { it.varselType == MotedeltakerVarselType.ENDRET.name })
        assertEquals(1, mote.arbeidsgiver.varselList.count { it.varselType == MotedeltakerVarselType.ENDRET.name })
        assertEquals("Et annet sted", mote.sted)
        assertEquals(nyBehandlerRef, mote.behandler?.behandlerRef)
        assertEquals(1, sentDialogmeldinger.size)
        assertEquals(DialogmeldingKode.INNKALLING.value, sentDialogmeldinger.first().dialogmeldingKode)
    }

    @Test
    fun `existing behandler gets ENDRET when tid sted changed`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, endringBehandler(), tidStedChanged = true))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(MoteEndringType.TID_STED), endringer(moteUuid))

        val mote = client.getMote()
        assertEquals(Dialogmote.Status.ENDRET.name, mote.status)
        assertEquals(newDialogmoteDTO.behandler!!.behandlerRef, mote.behandler?.behandlerRef)
        assertEquals(
            listOf(MotedeltakerVarselType.ENDRET.name, MotedeltakerVarselType.INNKALT.name),
            mote.behandler?.varselList?.map { it.varselType },
        )

        assertEquals(1, sentDialogmeldinger.size)
        val dialogmelding = sentDialogmeldinger.first()
        assertEquals(DialogmeldingKode.ENDRING.value, dialogmelding.dialogmeldingKode)
        val innkallingUuid = mote.behandler!!.varselList.last().uuid
        assertEquals(innkallingUuid, dialogmelding.dialogmeldingRefConversation)
        assertEquals(innkallingUuid, dialogmelding.dialogmeldingRefParent)
    }

    @Test
    fun `returns BadRequest when trying to change existing behandler`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, leggTilBehandler(), tidStedChanged = true))
        assertEquals(HttpStatusCode.BadRequest, response.status)

        assertNoChanges(client, newDialogmoteDTO.behandler!!.behandlerRef)
    }

    @Test
    fun `returns BadRequest when nothing changed`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, endringBehandler(), tidStedChanged = false))
        assertEquals(HttpStatusCode.BadRequest, response.status)

        assertNoChanges(client, newDialogmoteDTO.behandler!!.behandlerRef)
    }

    @Test
    fun `returns BadRequest when mote has behandler and behandler is missing`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, null, tidStedChanged = true))
        assertEquals(HttpStatusCode.BadRequest, response.status)

        assertNoChanges(client, newDialogmoteDTO.behandler!!.behandlerRef)
    }

    @Test
    fun `returns BadRequest when adding behandler without deltaker`() = testApplication {
        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
        val client = client()
        val moteUuid = client.createMote(newDialogmoteDTO)

        val response = client.endre(moteUuid, endreDTO(newDialogmoteDTO, endringBehandler(), tidStedChanged = true))
        assertEquals(HttpStatusCode.BadRequest, response.status)

        assertNoChanges(client, expectedBehandlerRef = null)
    }

    private fun endringer(moteUuid: String): List<MoteEndringType> =
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT e.type FROM MOTE_ENDRING e
                INNER JOIN MOTE_STATUS_ENDRET s ON s.id = e.mote_status_endret_id
                INNER JOIN MOTE m ON m.id = s.mote_id
                WHERE m.uuid = ? ORDER BY e.id
                """,
            ).use { ps ->
                ps.setString(1, moteUuid)
                ps.executeQuery().toList { MoteEndringType.valueOf(getString("type")) }
            }
        }

    private fun antallEndringer(): Int =
        database.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM MOTE_ENDRING").use { ps ->
                ps.executeQuery().toList { getInt(1) }.first()
            }
        }

    private suspend fun assertNoChanges(client: HttpClient, expectedBehandlerRef: String?) {
        assertEquals(0, antallEndringer())
        val mote = client.getMote()
        assertEquals(Dialogmote.Status.INNKALT.name, mote.status)
        assertEquals(expectedBehandlerRef, mote.behandler?.behandlerRef)
        assertTrue(mote.arbeidstaker.varselList.none { it.varselType == MotedeltakerVarselType.ENDRET.name })
        assertTrue(mote.arbeidsgiver.varselList.none { it.varselType == MotedeltakerVarselType.ENDRET.name })
        assertTrue(sentDialogmeldinger.isEmpty())
        verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(any()) }
    }
}
