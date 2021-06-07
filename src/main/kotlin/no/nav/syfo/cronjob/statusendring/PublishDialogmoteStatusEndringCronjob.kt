package no.nav.syfo.cronjob.statusendring

import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.cronjob.*
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import org.slf4j.LoggerFactory
import java.time.Duration

data class PublishDialogmoteStatusEndringResult(
    var updated: Int = 0,
    var failed: Int = 0,
)

class PublishDialogmoteStatusEndringCronjob(
    private val applicationState: ApplicationState,
    private val publishDialogmoteStatusEndringService: PublishDialogmoteStatusEndringService,
    private val leaderPodClient: LeaderPodClient,
) {
    suspend fun start() = coroutineScope {
        val (initialDelay, intervalDelay) = delays()
        log.info(
            "Scheduling start of ${PublishDialogmoteStatusEndringCronjob::class.simpleName}: {} ms, {} ms",
            StructuredArguments.keyValue("initialDelay", initialDelay),
            StructuredArguments.keyValue("intervalDelay", intervalDelay),
        )
        delay(initialDelay)

        while (applicationState.ready) {
            val job = launch { run() }
            delay(intervalDelay)
            if (job.isActive) {
                log.warn("Waiting for job to finish")
                job.join()
            }
        }
        log.info("Ending ${PublishDialogmoteStatusEndringCronjob::class.simpleName} due to failed liveness check ")
    }

    private fun run() {
        try {
            if (leaderPodClient.isLeader()) {
                dialogmoteStatusEndringPublishJob()
            } else {
                log.debug("Pod is not leader and will not perform job")
            }
        } catch (ex: Exception) {
            log.error("Exception in ${PublishDialogmoteStatusEndringCronjob::class.simpleName}. Job will run again after delay.", ex)
        }
    }

    fun dialogmoteStatusEndringPublishJob(): PublishDialogmoteStatusEndringResult {
        val result = PublishDialogmoteStatusEndringResult()

        val dialogmoteStatusEndretList = publishDialogmoteStatusEndringService.getDialogmoteStatuEndretToPublishList()
        dialogmoteStatusEndretList.forEach { dialogmoteStatusEndret ->
            try {
                publishDialogmoteStatusEndringService.publishAndUpdateDialogmoteStatusEndring(
                    dialogmoteStatusEndret = dialogmoteStatusEndret,
                )
                result.updated++
                COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE.inc()
            } catch (e: Exception) {
                log.error("Exception caught while attempting to publish DialogmoteStatusEndret", e)
                result.failed++
                COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_FAIL.inc()
            }
        }
        log.info(
            "Completed dialogmoteStatusEndring-publishing with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    private fun delays(): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(2).toMillis()
        val intervalDelay = Duration.ofMinutes(60).toMillis()
        return Pair(initialDelay, intervalDelay)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishDialogmoteStatusEndringCronjob::class.java)
    }
}
