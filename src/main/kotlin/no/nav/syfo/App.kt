package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import java.util.concurrent.TimeUnit
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.api.authentication.getWellKnown
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.isDevGcp
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.brev.behandler.kafka.kafkaBehandlerDialogmeldingProducerConfig
import no.nav.syfo.brev.esyfovarsel.EsyfovarselHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.brev.esyfovarsel.kafkaEsyfovarselConfig
import no.nav.syfo.client.altinn.createPort
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.dialogmelding.DialogmeldingService
import no.nav.syfo.dialogmelding.kafka.DialogmeldingConsumerService
import no.nav.syfo.dialogmelding.kafka.kafkaDialogmeldingConsumerConfig
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.kafkaIdenthendelseConsumerConfig
import no.nav.syfo.janitor.JanitorService
import no.nav.syfo.janitor.kafka.JanitorEventConsumer
import no.nav.syfo.janitor.kafka.JanitorEventStatusProducer
import no.nav.syfo.janitor.kafka.kafkaJanitorEventConsumerConfig
import no.nav.syfo.janitor.kafka.kafkaJanitorEventProducerConfig
import no.nav.syfo.testdata.reset.TestdataResetService
import no.nav.syfo.testdata.reset.kafka.TestdataResetConsumer
import no.nav.syfo.testdata.reset.kafka.kafkaTestdataResetConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.*

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    logger.info("isdialogmote starting with java version: " + Runtime.version())
    val environment = Environment()

    val behandlerDialogmeldingProducer = BehandlerDialogmeldingProducer(
        kafkaProducerBehandlerDialogmeldingBestilling = KafkaProducer<String, KafkaBehandlerDialogmeldingDTO>(
            kafkaBehandlerDialogmeldingProducerConfig(environment.kafka)
        ),
    )

    val esyfovarselProducer = EsyfovarselProducer(
        kafkaEsyfovarselProducer = KafkaProducer<String, EsyfovarselHendelse>(
            kafkaEsyfovarselConfig(environment.kafka)
        ),
    )
    val valkeyConfig = environment.valkeyConfig
    val cache = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.valkeyUsername)
                .password(valkeyConfig.valkeyPassword)
                .database(valkeyConfig.valkeyDB)
                .build()
        )
    )

    val altinnSoapClient = createPort(environment.altinnWsUrl)
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        valkeyStore = cache,
    )
    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenxClientId,
        tokenxEndpoint = environment.tokenxEndpoint,
        tokenxPrivateJWK = environment.tokenxPrivateJWK,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleBaseUrl = environment.isoppfolgingstilfelleUrl,
        isoppfolgingstilfelleClientId = environment.isoppfolgingstilfelleClientId,
        cache = cache,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
        valkeyStore = cache,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdV2Client = azureAdV2Client,
        syfobehandlendeenhetBaseUrl = environment.syfobehandlendeenhetUrl,
        syfobehandlendeenhetClientId = environment.syfobehandlendeenhetClientId,
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        azureAdV2Client = azureAdV2Client,
        cache = cache,
        clientId = environment.krrClientId,
        baseUrl = environment.krrUrl,
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.ispdfgenUrl
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdV2Client = azureAdV2Client,
        tilgangskontrollClientId = environment.istilgangskontrollClientId,
        tilgangskontrollBaseUrl = environment.istilgangskontrollUrl
    )
    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = environment.narmestelederUrl,
        narmestelederClientId = environment.narmestelederClientId,
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        cache = cache,
    )

    lateinit var behandlerVarselService: BehandlerVarselService
    lateinit var dialogmoterelasjonService: DialogmoterelasjonService
    lateinit var dialogmotestatusService: DialogmotestatusService

    val applicationEngineEnvironment = applicationEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
    }
    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                environment = environment
            )
            behandlerVarselService = BehandlerVarselService(
                database = applicationDatabase,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer
            )
            val arbeidstakerVarselService = ArbeidstakerVarselService(
                esyfovarselProducer = esyfovarselProducer,
            )
            val dialogmotedeltakerService = DialogmotedeltakerService(
                database = applicationDatabase,
                arbeidstakerVarselService = arbeidstakerVarselService
            )
            dialogmoterelasjonService = DialogmoterelasjonService(
                database = applicationDatabase,
                dialogmotedeltakerService = dialogmotedeltakerService
            )
            val moteStatusEndretRepository = MoteStatusEndretRepository(
                database = applicationDatabase,
            )
            dialogmotestatusService = DialogmotestatusService(
                oppfolgingstilfelleClient = oppfolgingstilfelleClient,
                moteStatusEndretRepository = moteStatusEndretRepository,
            )

            apiModule(
                applicationState = applicationState,
                esyfovarselProducer = esyfovarselProducer,
                behandlerVarselService = behandlerVarselService,
                database = applicationDatabase,
                environment = environment,
                wellKnownSelvbetjening = getWellKnown(environment.tokenxWellKnownUrl),
                wellKnownVeilederV2 = getWellKnown(environment.azureAppWellKnownUrl),
                altinnSoapClient = altinnSoapClient,
                dialogmotestatusService = dialogmotestatusService,
                dialogmoterelasjonService = dialogmoterelasjonService,
                dialogmotedeltakerService = dialogmotedeltakerService,
                arbeidstakerVarselService = arbeidstakerVarselService,
                pdlClient = pdlClient,
                behandlendeEnhetClient = behandlendeEnhetClient,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                oppfolgingstilfelleClient = oppfolgingstilfelleClient,
                kontaktinformasjonClient = kontaktinformasjonClient,
                pdfGenClient = pdfGenClient,
                narmesteLederClient = narmesteLederClient,
            )
            cronjobModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                cache = cache,
                dialogmotestatusService = dialogmotestatusService,
                dialogmoterelasjonService = dialogmoterelasjonService,
                arbeidstakerVarselService = arbeidstakerVarselService,
                moteStatusEndretRepository = moteStatusEndretRepository,
            )
            monitor.subscribe(ApplicationStarted) {
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
                val identhendelseService = IdenthendelseService(
                    database = applicationDatabase,
                    pdlClient = pdlClient,
                )
                val identhendelseConsumerService = IdenthendelseConsumerService(
                    kafkaConsumer = KafkaConsumer(kafkaIdenthendelseConsumerConfig(environment.kafka)),
                    applicationState = applicationState,
                    identhendelseService = identhendelseService,
                )
                launchBackgroundTask(applicationState = applicationState) {
                    identhendelseConsumerService.startConsumer()
                }

                val janitorService = JanitorService(
                    database = applicationDatabase,
                    dialogmotestatusService = dialogmotestatusService,
                    dialogmoterelasjonService = dialogmoterelasjonService,
                    janitorEventStatusProducer = JanitorEventStatusProducer(
                        kafkaProducer = KafkaProducer(kafkaJanitorEventProducerConfig(environment.kafka)),
                    ),
                )

                val janitorEventConsumer = JanitorEventConsumer(
                    kafkaConsumer = KafkaConsumer(kafkaJanitorEventConsumerConfig(environment.kafka)),
                    applicationState = applicationState,
                    janitorService = janitorService,
                )
                launchBackgroundTask(applicationState = applicationState) {
                    janitorEventConsumer.startConsumer()
                }

                if (environment.isDevGcp()) {
                    val testdataResetService = TestdataResetService(
                        database = applicationDatabase,
                    )

                    val testdataResetConsumer = TestdataResetConsumer(
                        kafkaConsumer = KafkaConsumer(kafkaTestdataResetConsumerConfig(environment.kafka)),
                        applicationState = applicationState,
                        testdataResetService = testdataResetService,
                    )

                    launchBackgroundTask(applicationState = applicationState) {
                        testdataResetConsumer.startConsumer()
                    }
                }
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
