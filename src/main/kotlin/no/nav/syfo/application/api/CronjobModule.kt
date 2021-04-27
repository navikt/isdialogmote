package no.nav.syfo.application.api

import io.ktor.application.*
import kotlinx.coroutines.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient

fun Application.cronjobModule(
    applicationState: ApplicationState,
    environment: Environment
) {
    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        applicationState = applicationState,
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
