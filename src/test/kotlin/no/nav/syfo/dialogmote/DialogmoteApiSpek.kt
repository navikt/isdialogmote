package no.nav.syfo.dialogmote

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.api.authentication.WellKnown
import no.nav.syfo.application.api.authentication.installJwtAuthentication
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.testhelper.mock.SyfopersonMock
import no.nav.syfo.testhelper.mock.VeilederTilgangskontrollMock
import no.nav.syfo.testhelper.testEnvironment
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

object DialogmoteApiSpek : Spek({

    describe("DialogmoteApiSpek") {

        with(TestApplicationEngine()) {
            start()

            val environment = testEnvironment()

            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val wellKnown = WellKnown(
                authorization_endpoint = "authorizationendpoint",
                token_endpoint = "tokenendpoint",
                jwks_uri = uri.toString(),
                issuer = "https://sts.issuer.net/myid"
            )

            val syfopersonMock = SyfopersonMock()
            val adressebeskyttelseClient = AdressebeskyttelseClient(
                syfopersonBaseUrl = syfopersonMock.url
            )
            val kontaktinformasjonClient = KontaktinformasjonClient(
                syfopersonBaseUrl = syfopersonMock.url
            )
            val tilgangskontrollMock = VeilederTilgangskontrollMock()
            val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
                tilgangskontrollBaseUrl = tilgangskontrollMock.url
            )
            val dialogmoteTilgangService = DialogmoteTilgangService(
                adressebeskyttelseClient = adressebeskyttelseClient,
                kontaktinformasjonClient = kontaktinformasjonClient,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient
            )

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            application.installJwtAuthentication(wellKnown, listOf(environment.loginserviceClientId))

            application.routing {
                authenticate {
                    registerDialogmoteApi(
                        dialogmoteTilgangService = dialogmoteTilgangService
                    )
                }
            }

            beforeGroup {
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()
            }

            afterGroup {
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)
            }

            describe("Get Dialogmoter for PersonIdent") {
                val url = "$dialogmoteApiBasepath$dialogmoteApiPersonIdentUrlPath"
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnown.issuer
                )

                it("should return status Unauthorized if no token is supplied") {
                    with(
                        handleRequest(HttpMethod.Get, url) {}
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status Forbidden if denied access to person") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_2_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("should return status Forbidden if denied person has Adressbeskyttese") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_ADRESSEBESKYTTET.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("should return status Forbidden if denied person has cannot receive digital documents") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_IKKE_VARSEL.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("should return DialogmoteList if request is successful") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = objectMapper.readValue<List<Any>>(response.content!!)

                        dialogmoteList.size shouldBeEqualTo 0
                    }
                }
            }
        }
    }
})
