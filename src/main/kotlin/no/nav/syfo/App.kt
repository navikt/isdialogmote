package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val server = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())

            val environment = Environment()

            connector {
                port = applicationPort
            }

            val applicationState = ApplicationState(
                alive = true,
                ready = false
            )

            val wellKnown = getWellKnown(environment.aadDiscoveryUrl)
            module {
                databaseModule(
                    environment = environment
                )
                apiModule(
                    applicationState = applicationState,
                    database = applicationDatabase,
                    environment = environment,
                    wellKnown = wellKnown
                )
            }

            applicationState.ready = true
        }
    )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = false)
}
