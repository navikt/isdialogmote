package no.nav.syfo.api.endpoints

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.syfo.api.dto.AvventDTO
import no.nav.syfo.api.dto.LukkAvventDTO
import no.nav.syfo.api.dto.QueryAvventDTO
import no.nav.syfo.api.dto.CreateAvventDTO
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ANNEN_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.setupApiAndClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val AVVENT_API_PATH = "/api/avvent"

class AvventApiTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val validToken =
        generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )

    private val createAvventDTO =
        CreateAvventDTO(
            frist = LocalDate.now().plusWeeks(2),
            personident = ARBEIDSTAKER_FNR.value,
            beskrivelse = "Avventer noe",
        )

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    @Nested
    inner class PostAvvent {
        @Test
        fun `returns OK when creating avvent`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post(AVVENT_API_PATH) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(createAvventDTO)
                    }
                assertEquals(HttpStatusCode.OK, response.status)
                val avvent = response.body<AvventDTO>()
                assertEquals(ARBEIDSTAKER_FNR.value, avvent.personident)
            }
        }

        @Test
        fun `returns Forbidden when veileder has no access to person`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post(AVVENT_API_PATH) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(createAvventDTO.copy(personident = ARBEIDSTAKER_VEILEDER_NO_ACCESS.value))
                    }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }

        @Test
        fun `returns Unauthorized when token is missing`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post(AVVENT_API_PATH) {
                        contentType(ContentType.Application.Json)
                        setBody(createAvventDTO)
                    }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Nested
    inner class PostAvventQuery {
        @Test
        fun `returns active avvent for personident`() {
            testApplication {
                val client = setupApiAndClient()
                client.post(AVVENT_API_PATH) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(createAvventDTO)
                }

                val response =
                    client.post("$AVVENT_API_PATH/query") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(QueryAvventDTO(personidenter = listOf(ARBEIDSTAKER_FNR.value)))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val avventList = response.body<List<AvventDTO>>()
                assertEquals(1, avventList.size)
                assertEquals(ARBEIDSTAKER_FNR.value, avventList.first().personident)
            }
        }

        @Test
        fun `returns empty list when no active avvent exists`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$AVVENT_API_PATH/query") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(QueryAvventDTO(personidenter = listOf(ARBEIDSTAKER_ANNEN_FNR.value)))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val avventList = response.body<List<Avvent>>()
                assertEquals(0, avventList.size)
            }
        }

        @Test
        fun `returns Forbidden when veileder has no access to person`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$AVVENT_API_PATH/query") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(QueryAvventDTO(personidenter = listOf(ARBEIDSTAKER_VEILEDER_NO_ACCESS.value)))
                    }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }

    @Nested
    inner class PostLukkAvvent {
        @Test
        fun `returns OK when lukking avvent`() {
            testApplication {
                val client = setupApiAndClient()
                client.post(AVVENT_API_PATH) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(createAvventDTO)
                }

                val response =
                    client.post("$AVVENT_API_PATH/lukk") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(LukkAvventDTO(personident = ARBEIDSTAKER_FNR.value))
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `avvent is no longer active after lukking`() {
            testApplication {
                val client = setupApiAndClient()
                client.post(AVVENT_API_PATH) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(createAvventDTO)
                }

                client.post("$AVVENT_API_PATH/lukk") {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(LukkAvventDTO(personident = ARBEIDSTAKER_FNR.value))
                }

                val queryResponse =
                    client.post("$AVVENT_API_PATH/query") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(QueryAvventDTO(personidenter = listOf(ARBEIDSTAKER_FNR.value)))
                    }
                assertEquals(HttpStatusCode.OK, queryResponse.status)
                val avventList = queryResponse.body<List<Avvent>>()
                assertTrue(avventList.isEmpty())
            }
        }

        @Test
        fun `returns OK when no active avvent exists`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$AVVENT_API_PATH/lukk") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(LukkAvventDTO(personident = ARBEIDSTAKER_FNR.value))
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `returns Forbidden when veileder has no access to person`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$AVVENT_API_PATH/lukk") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(LukkAvventDTO(personident = ARBEIDSTAKER_VEILEDER_NO_ACCESS.value))
                    }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }

        @Test
        fun `returns Unauthorized when token is missing`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$AVVENT_API_PATH/lukk") {
                        contentType(ContentType.Application.Json)
                        setBody(LukkAvventDTO(personident = ARBEIDSTAKER_FNR.value))
                    }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }
}
