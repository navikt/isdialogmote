package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.brukernotifikasjon.schemas.input.*
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
import no.nav.syfo.client.altinn.createPort
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.kafkaDineSykmeldteVarselProducerConfig
import no.nav.syfo.brev.narmesteleder.domain.DineSykmeldteHendelse
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.kafka.DialogmeldingConsumerService
import no.nav.syfo.dialogmelding.kafka.kafkaDialogmeldingConsumerConfig
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.kafkaIdenthendelseConsumerConfig
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

    val kafkaBrukernotifikasjonProducerProperties = kafkaBrukernotifikasjonProducerConfig(
        environment.kafka,
    )
    val brukernotifikasjonProducer = BrukernotifikasjonProducer(
        kafkaProducerBeskjed = KafkaProducer<NokkelInput, BeskjedInput>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerOppgave = KafkaProducer<NokkelInput, OppgaveInput>(kafkaBrukernotifikasjonProducerProperties),
        kafkaProducerDone = KafkaProducer<NokkelInput, DoneInput>(kafkaBrukernotifikasjonProducerProperties),
    )
    val dineSykmeldteVarselProducer = DineSykmeldteVarselProducer(
        kafkaProducerVarsel = KafkaProducer<String, DineSykmeldteHendelse>(
            kafkaDineSykmeldteVarselProducerConfig(
                environment.kafka
            )
        ),
    )
    val behandlerDialogmeldingProducer = BehandlerDialogmeldingProducer(
        kafkaProducerBehandlerDialogmeldingBestilling = KafkaProducer<String, KafkaBehandlerDialogmeldingDTO>(
            kafkaBehandlerDialogmeldingProducerConfig(environment.kafka)
        ),
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

    val altinnSoapClient = createPort(environment.altinnWsUrl)

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
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
                mqSender = mqSender,
                environment = environment,
                wellKnownSelvbetjening = getWellKnown(environment.tokenxWellKnownUrl),
                wellKnownVeilederV2 = getWellKnown(environment.azureAppWellKnownUrl),
                cache = cache,
                altinnSoapClient = altinnSoapClient,
            )
            cronjobModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                cache = cache,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info(
            "Application is ready, running Java VM ${Runtime.version()} on this number of processors: ${
            Runtime.getRuntime().availableProcessors()
            }"
        )
        val dialogmeldingService = DialogmeldingService(
            behandlerVarselService = behandlerVarselService,
        )
        val dialogmeldingConsumerService = DialogmeldingConsumerService(
            kafkaConsumer = KafkaConsumer(kafkaDialogmeldingConsumerConfig(environment.kafka)),
            applicationState = applicationState,
            dialogmeldingService = dialogmeldingService
        )
        val identhendelseConsumerService = IdenthendelseConsumerService(
            kafkaConsumer = KafkaConsumer(kafkaIdenthendelseConsumerConfig(environment.kafka)),
            applicationState = applicationState,
        )
        launchBackgroundTask(applicationState = applicationState) {
            logger.info("Starting dialogmelding kafka consumer")
            dialogmeldingConsumerService.startConsumer()
        }
        launchBackgroundTask(applicationState = applicationState) {
            identhendelseConsumerService.startConsumer()
        }
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
