package no.nav.syfo.dialogmote

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.moteplanlegger.domain.*
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class PostDialogmotePlanlagtApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(PostDialogmotePlanlagtApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>()
            justRun { mqSenderMock.sendMQMessage(any(), any()) }

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                startExternalMocks(
                    applicationMockMap = externalMockEnvironment.externalApplicationMockMap,
                    embeddedKafkaEnvironment = externalMockEnvironment.embeddedEnvironment,
                    embeddedRedisServer = externalMockEnvironment.redisServer,
                )
            }

            afterGroup {
                stopExternalMocks(
                    applicationMockMap = externalMockEnvironment.externalApplicationMockMap,
                    database = externalMockEnvironment.database,
                    embeddedKafkaEnvironment = externalMockEnvironment.embeddedEnvironment,
                    embeddedRedisServer = externalMockEnvironment.redisServer,
                )
            }

            describe("Create Dialogmote for PersonIdent from PlanlagtMoteUUID") {
                val validToken = generateJWT(
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {
                    val planlagtMoteDTO: PlanlagtMoteDTO? = externalMockEnvironment.syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_FNR.value]
                    val moteUUID: String? = planlagtMoteDTO?.moteUuid
                    val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                    val urlMoter = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"
                    it("should return OK if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                            clearMocks(mqSenderMock)
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
                            dialogmoteDTO.planlagtMoteUuid shouldBeEqualTo moteUUID
                            dialogmoteDTO.planlagtMoteBekreftetTidspunkt.shouldNotBeNull()

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo planlagtMoteDTO?.fnr
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1
                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo planlagtMoteDTO?.arbeidsgiver()?.orgnummer

                            dialogmoteDTO.tidStedList.size shouldBeEqualTo 1
                            val dialogmoteTidStedDTO = dialogmoteDTO.tidStedList.first()
                            dialogmoteTidStedDTO.sted shouldBeEqualTo planlagtMoteDTO?.tidStedValgt()?.sted

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        }
                    }
                }

                describe("Unhappy paths") {
                    val url = "$dialogmoteApiBasepath/${UUID.randomUUID()}"
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied access to person") {
                        val moteUUID: String? = externalMockEnvironment.syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_VEILEDER_NO_ACCESS.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied person has Adressbeskyttese") {
                        val moteUUID: String? = externalMockEnvironment.syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_ADRESSEBESKYTTET.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status Forbidden if denied person has cannot receive digital documents") {
                        val moteUUID: String? = externalMockEnvironment.syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_IKKE_VARSEL.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }

                    it("should return status InternalServerError if denied person with PlanlagtMote with Virksomhet does not have a leader for that Virksomhet") {
                        val moteUUID: String? = externalMockEnvironment.syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                            verify(exactly = 0) { mqSenderMock.sendMQMessage(any(), any()) }
                        }
                    }
                }
            }
        }
    }
})
