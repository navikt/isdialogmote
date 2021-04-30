package no.nav.syfo.cronjob.journalforing

import kotlinx.coroutines.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import org.slf4j.LoggerFactory
import java.time.Duration

data class JournalforingResult(
    var updated: Int = 0,
    var failed: Int = 0,
)

class DialogmoteVarselJournalforingCronjob(
    private val applicationState: ApplicationState,
    private val dialogmotedeltakerVarselJournalforingService: DialogmotedeltakerVarselJournalforingService,
    private val dokarkivClient: DokarkivClient,
    private val leaderPodClient: LeaderPodClient,
) {
    suspend fun start() = coroutineScope {
        val (initialDelay, intervalDelay) = delays()
        log.info("CRONJOB-TRACE: Scheduling start of job: initialDelay: $initialDelay ms, interval: $intervalDelay ms")
        delay(initialDelay)

        while (applicationState.alive) {
            val job = launch { run() }
            delay(intervalDelay)
            if (job.isActive) {
                log.warn("CRONJOB-TRACE: Waiting for job to finish")
                job.join()
            }
        }
        log.info("CRONJOB-TRACE: Ending job")
    }

    private suspend fun run() {
        try {
            if (leaderPodClient.isLeader()) {
                val resultat = dialogmoteVarselJournalforingJob()
                log.info("CRONJOB-TRACE: Completed job: failed=${resultat.failed}, updated=${resultat.updated}")
            } else {
                log.info("CRONJOB-TRACE: Pod is not leader and will not perform job")
            }
        } catch (ex: Exception) {
            log.error("CRONJOB-TRACE: exception in job. Job will run again after delay.", ex)
        }
    }

    private suspend fun dialogmoteVarselJournalforingJob(): JournalforingResult {
        val arbeidstakerVarselWithJournalpostList = dialogmotedeltakerVarselJournalforingService.getDialogmotedeltakerArbeidstakerVarselForJournalforingList()
        log.info("CRONJOB-TRACE: Receiving list of ArbeidstakerVarsel of size=${arbeidstakerVarselWithJournalpostList.size} for Journalforing")
        // TODO: Implement Journalforing
        dokarkivClient.journalfor()
        return JournalforingResult()
    }

    private fun delays(): Pair<Long, Long> {
        // TODO: Set approriate delay durations
        val initialDelay = Duration.ofMinutes(2).toMillis()
        val intervalDelay = Duration.ofMinutes(10).toMillis()
        return Pair(initialDelay, intervalDelay)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteVarselJournalforingCronjob::class.java)
    }
}
