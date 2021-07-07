package no.nav.syfo.cronjob

import io.ktor.application.*
import kotlinx.coroutines.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.statusendring.*
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import no.nav.syfo.dialogmote.ReferatJournalforingService
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment
) {
    val azureAdClient = AzureAdClient(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
    )
    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivClientId = environment.dokarkivClientId,
        dokarkivBaseUrl = environment.dokarkivUrl,
    )
    val dialogmotedeltakerVarselJournalforingService = DialogmotedeltakerVarselJournalforingService(
        database = database,
    )
    val referatJournalforingService = ReferatJournalforingService(
        database = database,
    )
    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        applicationState = applicationState,
        dialogmotedeltakerVarselJournalforingService = dialogmotedeltakerVarselJournalforingService,
        referatJournalforingService = referatJournalforingService,
        dokarkivClient = dokarkivClient,
        leaderPodClient = leaderPodClient,
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
        applicationState = applicationState,
        publishDialogmoteStatusEndringService = publishDialogmoteStatusEndringService,
        leaderPodClient = leaderPodClient,
    )

    if (environment.journalforingCronjobEnabled) {
        createListenerCronjob(
            applicationState = applicationState,
        ) {
            journalforDialogmoteVarslerCronjob.start()
        }
    } else {
        log.info("JournalforingCronjob is not enabled")
    }
    if (environment.publishDialogmoteStatusEndringCronjobEnabled) {
        createListenerCronjob(
            applicationState = applicationState,
        ) {
            publishDialogmoteStatusEndringCronjob.start()
        }
    } else {
        log.info("PublishDialogmoteStatusEndringCronjob is not enabled")
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
