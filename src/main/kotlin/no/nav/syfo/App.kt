package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.brukernotifikasjon.schemas.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.api.cronjobModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.kafkaBrukernotifikasjonProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState(
        alive = true,
        ready = false
    )

    val server = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())

            val environment = Environment()

            connector {
                port = applicationPort
            }

            val kafkaBrukernotifikasjonProducerProperties = kafkaBrukernotifikasjonProducerConfig(environment)
            val kafkaProducerOppgave = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerProperties)
            val kafkaProducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerProperties)
            val brukernotifikasjonProducer = BrukernotifikasjonProducer(
                kafkaProducerOppgave = kafkaProducerOppgave,
                kafkaProducerDone = kafkaProducerDone,
            )
            val mqSender = MQSender(environment)

            module {
                databaseModule(
                    environment = environment
                )
                apiModule(
                    applicationState = applicationState,
                    brukernotifikasjonProducer = brukernotifikasjonProducer,
                    database = applicationDatabase,
                    mqSender = mqSender,
                    environment = environment,
                    wellKnownSelvbetjening = getWellKnown(environment.loginserviceIdportenDiscoveryUrl),
                    wellKnownVeileder = getWellKnown(environment.aadDiscoveryUrl),
                )
                cronjobModule(
                    applicationState = applicationState,
                    environment = environment,
                )
            }
        }
    )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.environment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready")
    }
    server.start(wait = false)
}
