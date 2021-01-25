package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    val applicationState = ApplicationState()

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(applicationState)
            }

            it("Returns ok on is_alive") {
                applicationState.alive = true

                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_alive") {
                applicationState.ready = true

                with(handleRequest(HttpMethod.Get, "/is_ready")) {
                    println(response.status())
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = false,
                        ready = false
                    )
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
