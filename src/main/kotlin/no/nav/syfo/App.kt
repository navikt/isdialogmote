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
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.kafkaBrukernotifikasjonProducerConfig
import no.nav.syfo.varsel.narmesteleder.NarmesteLederVarselService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
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
            }
            val narmesteLederVarselService = NarmesteLederVarselService(
                env = environment,
                mqSender = mqSender
            )

            try {
                val dto = NarmesteLederDTO(
                    "Geir",
                    epost = "geir.arne.waagbo@nav.no",
                    tlf = "93248340",
                    aktoerId = "",
                    organisasjonsnavn = "NAV",
                    fomDato = LocalDate.now(),
                    orgnummer = "999999999"
                )
                narmesteLederVarselService.sendVarsel(LocalDateTime.now(), dto, MotedeltakerVarselType.INNKALT)
            } catch (exc: Exception) {
                log.warn("Tried sending MQ-message, got exception: ", exc)
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
