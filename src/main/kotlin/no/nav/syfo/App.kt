package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.brukernotifikasjon.schemas.*
import no.nav.syfo.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.kafkaBrukernotifikasjonProducerConfig
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.*
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.kafka.DialogmeldingConsumerService
import no.nav.syfo.dialogmelding.kafka.kafkaDialogmeldingConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.*
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    logger.info("isdialogmote starting with java version: " + Runtime.version())
    val environment = Environment()

    val kafkaBrukernotifikasjonProducerProperties = kafkaBrukernotifikasjonProducerConfig(environment)
    val brukernotifikasjonProducer = BrukernotifikasjonProducer(
        kafkaProducerBeskjed = KafkaProducer<Nokkel, Beskjed>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerOppgave = KafkaProducer<Nokkel, Oppgave>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerDone = KafkaProducer<Nokkel, Done>(kafkaBrukernotifikasjonProducerProperties),
    )
    val behandlerDialogmeldingProducer = BehandlerDialogmeldingProducer(
        kafkaProducerBehandlerDialogmeldingBestilling = KafkaProducer<String, KafkaBehandlerDialogmeldingDTO>(
            kafkaBehandlerDialogmeldingProducerConfig(environment.kafka)
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
    lateinit var behandlerVarselService: BehandlerVarselService

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
            behandlerVarselService = BehandlerVarselService(
                database = applicationDatabase,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer
            )
            apiModule(
                applicationState = applicationState,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                behandlerVarselService = behandlerVarselService,
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
        if (environment.toggleKafkaProcessingDialogmeldinger) {
            val dialogmeldingService = DialogmeldingService(
                behandlerVarselService = behandlerVarselService,
            )
            val dialogmeldingConsumerService = DialogmeldingConsumerService(
                kafkaConsumer = KafkaConsumer(kafkaDialogmeldingConsumerConfig(environment.kafka)),
                applicationState = applicationState,
                dialogmeldingService = dialogmeldingService
            )
            launchBackgroundTask(applicationState = applicationState) {
                logger.info("Starting dialogmelding kafka consumer")
                dialogmeldingConsumerService.startConsumer()
            }
        } else {
            logger.info("Kafka processing dialogmeldinger is not enabled")
        }
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
