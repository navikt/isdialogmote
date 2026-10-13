package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.dto.EndreDialogmoteBehandlerDTO
import no.nav.syfo.api.dto.EndreDialogmoteDTO
import no.nav.syfo.api.dto.EndreTidStedBegrunnelseDTO
import no.nav.syfo.api.endpoints.dialogmoteApiMoteEndrePath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.dialogmote.*
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
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

    @BeforeEach
    fun setup() {
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(any()) }
        justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearAllMocks()
    }

    private fun document(text: String) = listOf(
        DocumentComponentDTO(type = DocumentComponentType.PARAGRAPH, title = null, texts = listOf(text))
    )

    private fun begrunnelse(text: String) = EndreTidStedBegrunnelseDTO(text, document(text))

    private fun endreDTO(
        newDialogmoteDTO: no.nav.syfo.api.dto.NewDialogmoteDTO,
        behandler: EndreDialogmoteBehandlerDTO?,
        sameTidSted: Boolean = true,
    ) = EndreDialogmoteDTO(
        sted = if (sameTidSted) newDialogmoteDTO.tidSted.sted else "Et annet sted",
        tid = newDialogmoteDTO.tidSted.tid,
        videoLink = newDialogmoteDTO.tidSted.videoLink,
        arbeidstaker = begrunnelse("arbeidstaker"),
        arbeidsgiver = begrunnelse("arbeidsgiver"),
        behandler = behandler,
    )

    private fun newBehandler(avlysning: Boolean) = EndreDialogmoteBehandlerDTO(
        begrunnelse = "innkalling behandler",
        endringsdokument = document("innkalling behandler"),
        behandlerRef = nyBehandlerRef,
        behandlerNavn = "Ny Lege",
        behandlerKontor = "Nytt Kontor",
        personIdent = null,
        avlysning = if (avlysning) document("avlysning behandler") else null,
    )

    @Test
    fun `adds behandler to mote without behandler`() {
        testApplication {
            val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                esyfovarselProducer = esyfovarselProducerMock,
            )
            client.postMote(validToken, newDialogmoteDTO)
            val moteUuid = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first().uuid

            val sent = mutableListOf<KafkaBehandlerDialogmeldingDTO>()
            every { behandlerDialogmeldingProducer.sendDialogmelding(capture(sent)) } just Runs

            client.post("$dialogmoteApiV2Basepath/$moteUuid$dialogmoteApiMoteEndrePath") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(endreDTO(newDialogmoteDTO, newBehandler(avlysning = false)))
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            val mote = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first()
            assertEquals(Dialogmote.Status.ENDRET.name, mote.status)
            assertEquals(nyBehandlerRef, mote.behandler?.behandlerRef)
            assertEquals(
                listOf(MotedeltakerVarselType.INNKALT.name),
                mote.behandler?.varselList?.map { it.varselType }
            )
            assertTrue(mote.arbeidstaker.varselList.any { it.varselType == MotedeltakerVarselType.ENDRET.name })
            assertTrue(mote.arbeidsgiver.varselList.any { it.varselType == MotedeltakerVarselType.ENDRET.name })
            assertEquals(1, sent.size)
            assertEquals(nyBehandlerRef, sent.first().behandlerRef)
            assertEquals(DialogmeldingKode.INNKALLING.value, sent.first().dialogmeldingKode)
        }
    }

    @Test
    fun `replaces behandler on mote with behandler`() {
        testApplication {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                esyfovarselProducer = esyfovarselProducerMock,
            )
            client.postMote(validToken, newDialogmoteDTO)
            val moteUuid = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first().uuid

            val sent = mutableListOf<KafkaBehandlerDialogmeldingDTO>()
            every { behandlerDialogmeldingProducer.sendDialogmelding(capture(sent)) } just Runs

            client.post("$dialogmoteApiV2Basepath/$moteUuid$dialogmoteApiMoteEndrePath") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(endreDTO(newDialogmoteDTO, newBehandler(avlysning = true), sameTidSted = false))
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            val mote = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first()
            assertEquals(nyBehandlerRef, mote.behandler?.behandlerRef)
            assertEquals(2, sent.size)
            val avlysning = sent.first { it.behandlerRef == newDialogmoteDTO.behandler!!.behandlerRef }
            assertEquals(DialogmeldingKode.AVLYST.value, avlysning.dialogmeldingKode)
            val innkalling = sent.first { it.behandlerRef == nyBehandlerRef }
            assertEquals(DialogmeldingKode.INNKALLING.value, innkalling.dialogmeldingKode)
        }
    }

    @Test
    fun `unchanged behandler gets ENDRET only when tid or sted changed`() {
        testApplication {
            val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
            val client = setupApiAndClient(
                behandlerVarselService = behandlerVarselService,
                esyfovarselProducer = esyfovarselProducerMock,
            )
            client.postMote(validToken, newDialogmoteDTO)
            val moteUuid = client.getDialogmoter(validToken, ARBEIDSTAKER_FNR).body<List<DialogmoteDTO>>().first().uuid
            val sameBehandler = EndreDialogmoteBehandlerDTO(
                begrunnelse = "endring",
                endringsdokument = document("endring"),
            )

            client.post("$dialogmoteApiV2Basepath/$moteUuid$dialogmoteApiMoteEndrePath") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(endreDTO(newDialogmoteDTO, sameBehandler))
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }

            clearMocks(behandlerDialogmeldingProducer)
            val sent = mutableListOf<KafkaBehandlerDialogmeldingDTO>()
            every { behandlerDialogmeldingProducer.sendDialogmelding(capture(sent)) } just Runs

            client.post("$dialogmoteApiV2Basepath/$moteUuid$dialogmoteApiMoteEndrePath") {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(endreDTO(newDialogmoteDTO, sameBehandler, sameTidSted = false))
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            assertEquals(1, sent.size)
            assertEquals(DialogmeldingKode.TIDSTED.value, sent.first().dialogmeldingKode)
        }
    }
}
