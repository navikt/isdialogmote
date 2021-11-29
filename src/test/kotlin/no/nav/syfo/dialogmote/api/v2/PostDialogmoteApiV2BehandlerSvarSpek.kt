package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithBehandler
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class PostDialogmoteApiV2BehandlerSvarSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmoteApiV2BehandlerSvarSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
            val mqSenderMock = mockk<MQSenderInterface>()
            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
            }

            beforeEachTest {
                clearMocks(brukernotifikasjonProducer)
                justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                clearMocks(behandlerDialogmeldingProducer)
                justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                clearMocks(mqSenderMock)
                justRun { mqSenderMock.sendMQMessage(any(), any()) }
            }

            describe("Get Dialogmoter med svar fra behandler") {
                val svarTekst = "Fastlegen kommer i møtet"
                val urlMoter = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                val validToken = generateJWT(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    UserConstants.VEILEDER_IDENT,
                )

                it("should return dialogmote with svar fra behandler when svar finnes for varsel") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(UserConstants.ARBEIDSTAKER_FNR)
                    val createdBehandlerVarselUuid: UUID

                    with(
                        handleRequest(HttpMethod.Post, urlMoter) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlMoter) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        val behandlerVarselDTO = dialogmoteList.first().behandler!!.varselList.first()
                        createdBehandlerVarselUuid = UUID.fromString(behandlerVarselDTO.uuid)
                    }

                    behandlerVarselService.opprettVarselSvar(
                        varseltype = MotedeltakerVarselType.INNKALT,
                        svarType = DialogmoteSvarType.KOMMER,
                        svarTekst = svarTekst,
                        conversationRef = createdBehandlerVarselUuid,
                        parentRef = createdBehandlerVarselUuid,
                    )

                    with(
                        handleRequest(HttpMethod.Get, urlMoter) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                        val varselSvarDTO =
                            dialogmoteList.first().behandler!!.varselList.first().svar.first()
                        varselSvarDTO.uuid shouldNotBe null
                        varselSvarDTO.createdAt shouldNotBe null
                        varselSvarDTO.svarType shouldBeEqualTo DialogmoteSvarType.KOMMER.name
                        varselSvarDTO.tekst shouldBeEqualTo svarTekst
                    }
                }
            }
        }
    }
})
