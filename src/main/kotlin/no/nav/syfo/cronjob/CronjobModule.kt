package no.nav.syfo.cronjob

import io.ktor.server.application.Application
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.cronjob.dialogmotesvar.DialogmotesvarProducer
import no.nav.syfo.cronjob.dialogmotesvar.PublishDialogmotesvarCronjob
import no.nav.syfo.cronjob.dialogmotesvar.PublishDialogmotesvarService
import no.nav.syfo.cronjob.dialogmotesvar.KafkaDialogmotesvarProducerConfig
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.statusendring.DialogmoteStatusEndringProducer
import no.nav.syfo.cronjob.statusendring.PublishDialogmoteStatusEndringCronjob
import no.nav.syfo.cronjob.statusendring.PublishDialogmoteStatusEndringService
import no.nav.syfo.cronjob.statusendring.kafkaDialogmoteStatusEndringProducerConfig
import no.nav.syfo.dialogmote.*
import no.nav.syfo.cronjob.dialogmotesvar.KDialogmotesvar
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    cache: RedisStore,
    dialogmotestatusService: DialogmotestatusService,
    dialogmoterelasjonService: DialogmoterelasjonService,
    arbeidstakerVarselService: ArbeidstakerVarselService,
    moteStatusEndretRepository: MoteStatusEndretRepository,
) {
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        redisStore = cache,
    )
    val dokarkivClient = DokarkivClient(
        azureAdV2Client = azureAdV2Client,
        dokarkivClientId = environment.dokarkivClientId,
        dokarkivBaseUrl = environment.dokarkivUrl,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
        redisStore = cache,
    )
    val eregClient = EregClient(
        baseUrl = environment.eregUrl,
    )
    val pdfService = PdfService(
        database = database,
    )
    val dialogmotedeltakerVarselJournalpostService = DialogmotedeltakerVarselJournalpostService(
        database = database,
    )
    val referatJournalpostService = ReferatJournalpostService(
        database = database,
    )
    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        pdfService = pdfService,
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
        eregClient = eregClient,
        isJournalforingRetryEnabled = environment.isJournalforingRetryEnabled,
    )

    val kafkaDialogmoteStatusEndringProducerProperties = kafkaDialogmoteStatusEndringProducerConfig(environment.kafka)
    val kafkaDialogmoteStatusEndringProducer = KafkaProducer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringProducerProperties
    )
    val kafkaDialogmotesvarProducerProperties = KafkaDialogmotesvarProducerConfig(environment.kafka)

    val kafkaDialogmotesvarProducer = KafkaProducer<String, KDialogmotesvar>(
        kafkaDialogmotesvarProducerProperties
    )
    val dialogmoteStatusEndringProducer = DialogmoteStatusEndringProducer(
        kafkaDialogmoteStatusEndringProducer = kafkaDialogmoteStatusEndringProducer,
    )
    val dialogmotesvarProducer = DialogmotesvarProducer(
        kafkaDialogmotesvarProducer = kafkaDialogmotesvarProducer,
    )
    val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
        database = database,
        dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
        moteStatusEndretRepository = moteStatusEndretRepository,
    )
    val publishDialogmotesvarService = PublishDialogmotesvarService(
        database = database,
        dialogmotesvarProducer = dialogmotesvarProducer,
    )
    val publishDialogmoteStatusEndringCronjob = PublishDialogmoteStatusEndringCronjob(
        publishDialogmoteStatusEndringService = publishDialogmoteStatusEndringService,
    )
    val cronjobRunner = DialogmoteCronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val journalpostDistribusjonCronjob = DialogmoteJournalpostDistribusjonCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        arbeidstakerVarselService = arbeidstakerVarselService,
    )
    val publishDialogmotesvarCronjob = PublishDialogmotesvarCronjob(
        publishDialogmotesvarService = publishDialogmotesvarService
    )
    val dialogmoteOutdatedCronjob = DialogmoteOutdatedCronjob(
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        outdatedDialogmoterCutoff = environment.outdatedDialogmoteCutoff,
        database = database,
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = journalforDialogmoteVarslerCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = publishDialogmoteStatusEndringCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = journalpostDistribusjonCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = publishDialogmotesvarCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = dialogmoteOutdatedCronjob)
    }
}
