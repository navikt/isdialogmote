package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.endpoints.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_READONLY
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.setupApiAndClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TilgangApiV2Test {
    private val externalMockEnvironment = ExternalMockEnvironment.getInstance()
    private val database = externalMockEnvironment.database

    private val urlMote = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

    private val validTokenFullAccess = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT,
    )
    private val validTokenReadOnly = generateJWTNavIdent(
        externalMockEnvironment.environment.aadAppClient,
        externalMockEnvironment.wellKnownVeilederV2.issuer,
        VEILEDER_IDENT_READONLY,
    )
    private val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_FNR)

    @Nested
    @DisplayName("Read-only tilgang")
    inner class ReadOnlyTilgang {
        @Test
        fun `veileder med lesetilgang kan hente dialogmoter`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(urlMote) {
                    bearerAuth(validTokenReadOnly)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `veileder med lesetilgang kan ikke opprette dialogmote`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.post(urlMote) {
                    bearerAuth(validTokenReadOnly)
                    contentType(ContentType.Application.Json)
                    setBody(newDialogmoteDTO)
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }

    @Nested
    @DisplayName("Full tilgang")
    inner class FullTilgang {
        @Test
        fun `veileder med full tilgang kan hente dialogmoter`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(urlMote) {
                    bearerAuth(validTokenFullAccess)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

        @Test
        fun `veileder med full tilgang kan opprette dialogmote`() {
            try {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlMote) {
                        bearerAuth(validTokenFullAccess)
                        contentType(ContentType.Application.Json)
                        setBody(newDialogmoteDTO)
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                }
            } finally {
                database.dropData()
            }
        }
    }
}
