package no.nav.syfo.application.api

import io.ktor.application.*
import kotlinx.coroutines.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService

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
    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        applicationState = applicationState,
        dialogmotedeltakerVarselJournalforingService = dialogmotedeltakerVarselJournalforingService,
        dokarkivClient = dokarkivClient,
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
