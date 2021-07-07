package no.nav.syfo.brev.narmesteleder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.narmesteleder.domain.NarmesteLederBrevDTO
import no.nav.syfo.dialogmote.api.v1.dialogmoteApiBasepath
import no.nav.syfo.dialogmote.api.v1.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NarmesteLederBrevSpek : Spek({
    val objectMapper: ObjectMapper = apiConsumerObjectMapper()

    describe(NarmesteLederBrevSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
            justRun { brukernotifikasjonProducer.sendDone(any(), any()) }

            val mqSenderMock = mockk<MQSenderInterface>(relaxed = true)

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                mqSenderMock = mqSenderMock,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(UserConstants.ARBEIDSTAKER_FNR)
                val urlMote = "$dialogmoteApiBasepath/$dialogmoteApiPersonIdentUrlPath"
                val validTokenSelvbetjening = generateJWT(
                    audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                    subject = UserConstants.NARMESTELEDER_FNR.value,
                )
                val validTokenVeileder = generateJWT(
                    externalMockEnvironment.environment.loginserviceClientId,
                    externalMockEnvironment.wellKnownVeileder.issuer,
                    UserConstants.VEILEDER_IDENT,
                )

                it("Should return OK") {
                    with( // Create a dialogmote before we can test how to retrieve it
                        handleRequest(HttpMethod.Post, urlMote) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenVeileder))
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        verify(exactly = 1) { mqSenderMock.sendMQMessage(MotedeltakerVarselType.INNKALT, any()) }
                        clearMocks(mqSenderMock)
                    }

                    with(
                        handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader("personIdentAT", UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 1

                        val narmesteLederBrevDTO = nlBrevList.first()
                        narmesteLederBrevDTO.shouldNotBeNull()
                        narmesteLederBrevDTO.lestDato.shouldBeNull()
                    }
                }

                it("Should return OK when no brev exists") {
                    with(
                        handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            addHeader("personIdentAT", UserConstants.ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val nlBrevList = objectMapper.readValue<List<NarmesteLederBrevDTO>>(response.content!!)
                        nlBrevList.size shouldBeEqualTo 0
                    }
                }
            }

            describe("Error handling") {
                it("Should return BAD REQUEST when no personIdentAT is provided") {
                    val validTokenSelvbetjening = generateJWT(
                        audience = externalMockEnvironment.environment.loginserviceIdportenAudience.first(),
                        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                        subject = UserConstants.NARMESTELEDER_FNR.value,
                    )
                    with(
                        handleRequest(HttpMethod.Get, narmestelederBrevApiPath) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validTokenSelvbetjening))
                            addHeader(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
