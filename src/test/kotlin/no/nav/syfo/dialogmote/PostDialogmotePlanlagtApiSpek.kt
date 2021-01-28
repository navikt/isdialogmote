package no.nav.syfo.dialogmote

import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.testing.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.dialogmote.api.dialogmoteApiBasepath
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
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

            val modiasyforestMock = ModiasyforestMock()
            val syfomoteadminMock = SyfomoteadminMock()
            val syfopersonMock = SyfopersonMock()
            val tilgangskontrollMock = VeilederTilgangskontrollMock()

            val applicationState = testAppState()

            val database = TestDatabase()

            val environment = testEnvironment(
                modiasyforestUrl = modiasyforestMock.url,
                syfomoteadminUrl = syfomoteadminMock.url,
                syfopersonUrl = syfopersonMock.url,
                syfotilgangskontrollUrl = tilgangskontrollMock.url
            )

            val wellKnown = wellKnownMock()

            application.apiModule(
                applicationState = applicationState,
                database = database,
                environment = environment,
                wellKnown = wellKnown
            )

            beforeGroup {
                modiasyforestMock.server.start()
                syfomoteadminMock.server.start()
                syfopersonMock.server.start()
                tilgangskontrollMock.server.start()
            }

            afterGroup {
                modiasyforestMock.server.stop(1L, 10L)
                syfomoteadminMock.server.stop(1L, 10L)
                syfopersonMock.server.stop(1L, 10L)
                tilgangskontrollMock.server.stop(1L, 10L)

                database.stop()
            }

            describe("Create Dialogmote for PersonIdent from PlanlagtMoteUUID") {
                val validToken = generateJWT(
                    environment.loginserviceClientId,
                    wellKnown.issuer
                )
                describe("Happy path") {
                    val moteUUID: String? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_FNR.value]?.moteUuid
                    val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                    it("should return OK if request is successful") {
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
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
                        }
                    }

                    it("should return status Forbidden if denied access to person") {
                        val moteUUID: String? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_VEILEDER_NO_ACCESS.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if denied person has Adressbeskyttese") {
                        val moteUUID: String? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_ADRESSEBESKYTTET.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status Forbidden if denied person has cannot receive digital documents") {
                        val moteUUID: String? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_IKKE_VARSEL.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }

                    it("should return status InternalServerError if denied person with PlanlagtMote with Virksomhet does not have a leader for that Virksomhet") {
                        val moteUUID: String? = syfomoteadminMock.personIdentMoteMap[ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value]?.moteUuid
                        val urlMoteUUID = "$dialogmoteApiBasepath/$moteUUID"
                        with(
                            handleRequest(HttpMethod.Post, urlMoteUUID) {
                                addHeader(Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                        }
                    }
                }
            }
        }
    }
})
