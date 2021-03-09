package no.nav.syfo.dialogmote

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.justRun
import io.mockk.mockk
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.dialogmote.api.*
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.NewDialogmoteTidSted
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.mock.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class FerdigstillDialogmoteApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(FerdigstillDialogmoteApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
            val modiasyforestMock = ModiasyforestMock()
            val syfomoteadminMock = SyfomoteadminMock()
            val syfopersonMock = SyfopersonMock()
            val tilgangskontrollMock = VeilederTilgangskontrollMock()

            val applicationState = testAppState()

            val database = TestDatabase()

            val embeddedEnvironment = KafkaEnvironment(
                autoStart = false,
                withSchemaRegistry = false,
                topicNames = listOf(
                    BRUKERNOTIFIKASJON_OPPGAVE_TOPIC,
                    BRUKERNOTIFIKASJON_DONE_TOPIC,
                )
            )

            val environment = testEnvironment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
                isdialogmotepdfgenUrl = isdialogmotepdfgenMock.url,
                modiasyforestUrl = modiasyforestMock.url,
                syfomoteadminUrl = syfomoteadminMock.url,
                syfopersonUrl = syfopersonMock.url,
                syfotilgangskontrollUrl = tilgangskontrollMock.url
            )

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()

            val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
            val wellKnownVeileder = wellKnownSelvbetjeningMock()

            application.apiModule(
                applicationState = applicationState,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                database = database,
                environment = environment,
                wellKnownSelvbetjening = wellKnownSelvbetjening,
                wellKnownVeileder = wellKnownVeileder,
            )

            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                isdialogmotepdfgenMock.server.start()
                modiasyforestMock.server.start()
                syfomoteadminMock.server.start()
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()

                embeddedEnvironment.start()
            }

            afterGroup {
                isdialogmotepdfgenMock.server.stop(1L, 10L)
                modiasyforestMock.server.stop(1L, 10L)
                syfomoteadminMock.server.stop(1L, 10L)
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)

                database.stop()
                embeddedEnvironment.tearDown()
            }

            describe("Ferdigstill Dialogmote") {
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnownVeileder.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val planlagtMoteDTO: PlanlagtMoteDTO? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_FNR.value]
                    val planlagtmoteUUID: String? = planlagtMoteDTO?.moteUuid
                    val urlPlanlagtMoteUUID = "$dialogmoteApiBasepath/$planlagtmoteUUID"

                    val urlMoter = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlPlanlagtMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        val createdDialogmoteUUID: String

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDFerdigstill = "$dialogmoteApiBasepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        val newDialogmoteTidSted = NewDialogmoteTidSted(
                            sted = "Et annet sted",
                            tid = planlagtMoteDTO?.tidStedValgt()?.tid?.plusDays(1) ?: LocalDateTime.now().plusDays(2)
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUIDFerdigstill) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.planlagtMoteUuid shouldBeEqualTo planlagtmoteUUID
                            dialogmoteDTO.planlagtMoteBekreftetTidspunkt.shouldNotBeNull()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo planlagtMoteDTO?.fnr
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo planlagtMoteDTO?.arbeidsgiver()?.orgnummer

                            dialogmoteDTO.tidStedList.size shouldBeEqualTo 1
                            val dialogmoteTidStedDTO = dialogmoteDTO.tidStedList.first()
                            dialogmoteTidStedDTO.sted shouldBeEqualTo planlagtMoteDTO?.tidStedValgt()?.sted
                        }
                    }
                }
            }
        }
    }
})
