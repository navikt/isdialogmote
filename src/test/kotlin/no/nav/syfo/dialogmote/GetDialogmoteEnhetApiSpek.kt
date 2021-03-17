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
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.dialogmoteApiEnhetUrlPath
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.createNewDialogmotePlanlagtWithReferences
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.ENHET_NR_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateNewDialogmotePlanlagt
import no.nav.syfo.testhelper.mock.*
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GetDialogmoteEnhetApiSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe("DialogmoteApiSpek") {

        with(TestApplicationEngine()) {
            start()

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
                modiasyforestUrl = modiasyforestMock.url,
                syfomoteadminUrl = syfomoteadminMock.url,
                syfopersonUrl = syfopersonMock.url,
                syfotilgangskontrollUrl = tilgangskontrollMock.url
            )

            val redisServer = testRedis(environment)

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
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()
                embeddedEnvironment.start()
                redisServer.start()
            }

            afterGroup {
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)

                database.stop()
                embeddedEnvironment.tearDown()
                redisServer.stop()
            }

            describe("Get Dialogmoter for EnhetNr") {
                val urlEnhetAccess = "$dialogmoteApiBasepath$dialogmoteApiEnhetUrlPath/${ENHET_NR.value}"
                val urlEnhetNoAccess = "$dialogmoteApiBasepath$dialogmoteApiEnhetUrlPath/${ENHET_NR_NO_ACCESS.value}"
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnownVeileder.issuer,
                    VEILEDER_IDENT,
                )
                describe("Happy path") {

                    val newDialogmote = generateNewDialogmotePlanlagt(ARBEIDSTAKER_FNR)
                    val newDialogmoteAdressebeskyttet = generateNewDialogmotePlanlagt(ARBEIDSTAKER_ADRESSEBESKYTTET)
                    database.connection.use { connection ->
                        connection.createNewDialogmotePlanlagtWithReferences(
                            newDialogmotePlanlagt = newDialogmote
                        )
                        connection.createNewDialogmotePlanlagtWithReferences(
                            newDialogmotePlanlagt = newDialogmoteAdressebeskyttet
                        )
                    }

                    it("should return DialogmoteList if request is successful") {
                        with(
                            handleRequest(HttpMethod.Get, urlEnhetAccess) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo newDialogmote.tildeltEnhet
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmote.arbeidstaker.personIdent.value
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmote.arbeidsgiver.virksomhetsnummer.value

                            dialogmoteDTO.tidStedList.size shouldBeEqualTo 1
                            val dialogmoteTidStedDTO = dialogmoteDTO.tidStedList.first()
                            dialogmoteTidStedDTO.sted shouldBeEqualTo newDialogmote.tidSted.sted
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlEnhetAccess) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status Forbidden if denied access to Enhet") {
                        with(
                            handleRequest(HttpMethod.Get, urlEnhetNoAccess) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
