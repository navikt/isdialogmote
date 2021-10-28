package no.nav.syfo.cronjob

import io.ktor.application.*
import kotlinx.coroutines.*
import no.nav.syfo.application.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.statusendring.*
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    cache: RedisStore,
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
    val journalpostdistribusjonClient = JournalpostdistribusjonClient(
        azureAdV2Client = azureAdV2Client,
        dokdistFordelingClientId = environment.dokdistFordelingClientId,
        dokdistFordelingBaseUrl = environment.dokdistFordelingUrl
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
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
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
    )

    val kafkaDialogmoteStatusEndringProducerProperties = kafkaDialogmoteStatusEndringProducerConfig(environment)
    val kafkaDialogmoteStatusEndringProducer = KafkaProducer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringProducerProperties
    )

    val dialogmoteStatusEndringProducer = DialogmoteStatusEndringProducer(
        kafkaDialogmoteStatusEndringProducer = kafkaDialogmoteStatusEndringProducer,
    )
    val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
        database = database,
        dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
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
        journalpostdistribusjonClient = journalpostdistribusjonClient
    )

    if (environment.journalforingCronjobEnabled) {
        createListenerCronjob(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = journalforDialogmoteVarslerCronjob)
        }
    } else {
        log.info("JournalforingCronjob is not enabled")
    }
    if (environment.publishDialogmoteStatusEndringCronjobEnabled) {
        createListenerCronjob(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = publishDialogmoteStatusEndringCronjob)
        }
    } else {
        log.info("PublishDialogmoteStatusEndringCronjob is not enabled")
    }
    if (environment.allowVarselMedFysiskBrev) {
        createListenerCronjob(
            applicationState = applicationState
        ) {
            cronjobRunner.start(cronjob = journalpostDistribusjonCronjob)
        }
    } else {
        log.info("DialogmoteJournalpostDistribusjonCronjob not started due to allowVarselMedFysiskBrev not enabled")
    }
}

fun Application.createListenerCronjob(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job = GlobalScope.launch {
    try {
        action()
    } catch (ex: Exception) {
        log.error("Something went wrong, terminating application", ex)
    } finally {
        applicationState.alive = false
        applicationState.ready = false
    }
}
