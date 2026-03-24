package no.nav.syfo.api.endpoints

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.syfo.api.dto.MotebehovVurderingDTO
import no.nav.syfo.api.dto.MotebehovTilbakemeldingDTO
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.setupApiAndClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val MOTEBEHOV_API_PATH = "/api/motebehov"

class MotebehovApiTest {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val validToken =
        generateJWTNavIdent(
            externalMockEnvironment.environment.aadAppClient,
            externalMockEnvironment.wellKnownVeilederV2.issuer,
            VEILEDER_IDENT,
        )

    private val motebehovVurderingDTO =
        MotebehovVurderingDTO(
            personident = ARBEIDSTAKER_FNR.value,
            harBehovForMote = true,
            tilbakemeldinger = listOf(
                MotebehovTilbakemeldingDTO(
                    varseltekst = "Test tilbakemelding",
                    motebehovId = "test-motebehov-id",
                ),
            ),
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
    inner class PostBehandleMotebehov {
        @Test
        fun `returns OK when behandling motebehov`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$MOTEBEHOV_API_PATH/vurderinger") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(motebehovVurderingDTO)
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `returns OK when behandling motebehov with multiple tilbakemeldinger`() {
            testApplication {
                val client = setupApiAndClient()
                val dto = motebehovVurderingDTO.copy(
                    tilbakemeldinger = listOf(
                        MotebehovTilbakemeldingDTO(
                            varseltekst = "Første tilbakemelding",
                            motebehovId = "motebehov-id-1",
                        ),
                        MotebehovTilbakemeldingDTO(
                            varseltekst = "Andre tilbakemelding",
                            motebehovId = "motebehov-id-2",
                        ),
                    ),
                )
                val response =
                    client.post("$MOTEBEHOV_API_PATH/vurderinger") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(dto)
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `returns OK when behandling motebehov with empty tilbakemeldinger`() {
            testApplication {
                val client = setupApiAndClient()
                val dto = motebehovVurderingDTO.copy(tilbakemeldinger = emptyList())
                val response =
                    client.post("$MOTEBEHOV_API_PATH/vurderinger") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(dto)
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `returns Forbidden when veileder has no access to person`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$MOTEBEHOV_API_PATH/vurderinger") {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(motebehovVurderingDTO.copy(personident = ARBEIDSTAKER_VEILEDER_NO_ACCESS.value))
                    }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }

        @Test
        fun `returns Unauthorized when token is missing`() {
            testApplication {
                val client = setupApiAndClient()
                val response =
                    client.post("$MOTEBEHOV_API_PATH/vurderinger") {
                        contentType(ContentType.Application.Json)
                        setBody(motebehovVurderingDTO)
                    }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }
}
