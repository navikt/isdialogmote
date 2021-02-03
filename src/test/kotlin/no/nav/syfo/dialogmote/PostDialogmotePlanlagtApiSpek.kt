package no.nav.syfo.dialogmote

import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.mock.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class PostDialogmotePlanlagtApiSpek : Spek({

    describe(PostDialogmotePlanlagtApiSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val syfopersonMock = SyfopersonMock()
            val tilgangskontrollMock = VeilederTilgangskontrollMock()

            val applicationState = testAppState()

            val environment = testEnvironment(
                syfopersonMock.url,
                tilgangskontrollMock.url
            )

            val wellKnown = wellKnownMock()

            application.apiModule(
                applicationState = applicationState,
                environment = environment,
                wellKnown = wellKnown
            )

            beforeGroup {
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()
            }

            afterGroup {
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)
            }

            describe("Create Dialogmote for PersonIdent from PlanlagtMoteUUID") {
                val planlagtmoteuuid = UUID.randomUUID()
                val url = "$dialogmoteApiBasepath/$planlagtmoteuuid"
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnown.issuer
                )
                describe("Happy path") {
                    it("should return OK if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_2_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if denied person has Adressbeskyttese") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_ADRESSEBESKYTTET.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if denied person has cannot receive digital documents") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_IKKE_VARSEL.value)
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
