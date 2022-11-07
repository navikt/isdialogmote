package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import java.util.concurrent.TimeUnit
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.kafkaBrukernotifikasjonProducerConfig
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.brev.behandler.kafka.kafkaBehandlerDialogmeldingProducerConfig
import no.nav.syfo.brev.esyfovarsel.EsyfovarselHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.kafkaEsyfovarselConfig
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.kafkaDineSykmeldteVarselProducerConfig
import no.nav.syfo.brev.narmesteleder.domain.DineSykmeldteHendelse
import no.nav.syfo.client.altinn.createPort
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.kafka.DialogmeldingConsumerService
import no.nav.syfo.dialogmelding.kafka.kafkaDialogmeldingConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

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

    val esyfovarselProducer = EsyfovarselProducer(
        kafkaEsyfovarselProducer = KafkaProducer<String, EsyfovarselHendelse>(
            kafkaEsyfovarselConfig(environment.kafka)
        ),
    )

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
                esyfovarselProducer = esyfovarselProducer,
                database = applicationDatabase,
                environment = environment,
                wellKnownSelvbetjening = getWellKnown(environment.tokenxWellKnownUrl),
                wellKnownVeilederV2 = getWellKnown(environment.azureAppWellKnownUrl),
                cache = cache,
                altinnSoapClient = altinnSoapClient,
                mqSender = mqSender,
                dineSykmeldteVarselProducer = dineSykmeldteVarselProducer
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
        launchBackgroundTask(applicationState = applicationState) {
            logger.info("Starting dialogmelding kafka consumer")
            dialogmeldingConsumerService.startConsumer()
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
