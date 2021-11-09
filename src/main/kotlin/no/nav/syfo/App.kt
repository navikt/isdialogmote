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
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.kafkaBrukernotifikasjonProducerConfig
import no.nav.syfo.brev.behandler.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.*
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState(
        alive = true,
        ready = false
    )
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val kafkaBrukernotifikasjonProducerProperties = kafkaBrukernotifikasjonProducerConfig(environment)
    val brukernotifikasjonProducer = BrukernotifikasjonProducer(
        kafkaProducerBeskjed = KafkaProducer<Nokkel, Beskjed>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerOppgave = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerProperties),
    )
    val behandlerDialogmeldingProducer = BehandlerDialogmeldingProducer(
        kafkaProducerBehandlerDialogmeldingBestilling = KafkaProducer<String, KafkaBehandlerDialogmeldingDTO>(
            kafkaBehandlerDialogmeldingProducerConfig(environment)
        ),
        allowVarslingBehandler = environment.allowMotedeltakerBehandler,
    )
    val mqSender = MQSender(environment)
    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            environment.redisHost,
            environment.redisPort,
            Protocol.DEFAULT_TIMEOUT,
            environment.redisSecret
        )
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            databaseModule(
                environment = environment
            )
            apiModule(
                applicationState = applicationState,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
                database = applicationDatabase,
                mqSender = mqSender,
                environment = environment,
                wellKnownSelvbetjening = getWellKnown(environment.loginserviceIdportenDiscoveryUrl),
                wellKnownVeilederV2 = getWellKnown(environment.azureAppWellKnownUrl),
                cache = cache,
            )
            cronjobModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                cache = cache,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready")
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = false)
}
