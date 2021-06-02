package no.nav.syfo.cronjob.journalforing

import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import no.nav.syfo.dialogmote.ReferatJournalforingService
import no.nav.syfo.dialogmote.domain.*
import org.slf4j.LoggerFactory
import java.time.Duration

data class JournalforingResult(
    var updated: Int = 0,
    var failed: Int = 0,
)

class DialogmoteVarselJournalforingCronjob(
    private val applicationState: ApplicationState,
    private val dialogmotedeltakerVarselJournalforingService: DialogmotedeltakerVarselJournalforingService,
    private val referatJournalforingService: ReferatJournalforingService,
    private val dokarkivClient: DokarkivClient,
    private val leaderPodClient: LeaderPodClient,
) {
    suspend fun start() = coroutineScope {
        val (initialDelay, intervalDelay) = delays()
        log.info(
            "Scheduling start of DialogmoteVarselJournalforingCronjob: {} ms, {} ms",
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
        log.info("Ending DialogmoteVarselJournalforingCronjob due to failed liveness check ")
    }

    private suspend fun run() {
        try {
            if (leaderPodClient.isLeader()) {
                dialogmoteVarselJournalforingJob()
                referatJournalforingJob()
            } else {
                log.debug("Pod is not leader and will not perform job")
            }
        } catch (ex: Exception) {
            log.error("Exception in DialogmoteVarselJournalforingCronjob. Job will run again after delay.", ex)
        }
    }

    suspend fun dialogmoteVarselJournalforingJob(): JournalforingResult {
        val journalforingResult = JournalforingResult()

        val arbeidstakerVarselForJournalforingList = dialogmotedeltakerVarselJournalforingService.getDialogmotedeltakerArbeidstakerVarselForJournalforingList()
        arbeidstakerVarselForJournalforingList.forEach { (personIdent, arbeidstakerVarsel) ->
            try {
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = arbeidstakerVarsel.toJournalpostRequest(
                        personIdent = personIdent,
                    ),
                )?.journalpostId

                journalpostId?.let { it ->
                    dialogmotedeltakerVarselJournalforingService.updateJournalpostId(
                        arbeidstakerVarsel,
                        it,
                    )
                    journalforingResult.updated++
                    COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE.inc()
                } ?: throw RuntimeException("Failed to Journalfor ArbeidstakerVarsel: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidstakerVarsel", e)
                journalforingResult.failed++
                COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL.inc()
            }
        }
        log.info(
            "Completed varsel-journalforing with result: {}, {}",
            StructuredArguments.keyValue("failed", journalforingResult.failed),
            StructuredArguments.keyValue("updated", journalforingResult.updated),
        )
        return journalforingResult
    }

    suspend fun referatJournalforingJob(): JournalforingResult {
        val journalforingResult = JournalforingResult()

        val referatList = referatJournalforingService.getDialogmoteReferatJournalforingList()
        referatList.forEach { (personIdentNumber, referat) ->
            try {
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = referat.toJournalforingRequest(personIdentNumber)
                )?.journalpostId

                journalpostId?.let { it ->
                    referatJournalforingService.updateJournalpostIdForReferat(
                        referat,
                        it,
                    )
                    journalforingResult.updated++
                    COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE.inc()
                } ?: throw RuntimeException("Failed to Journalfor Referat: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
                COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL.inc()
            }
        }
        log.info(
            "Completed referat-journalforing with result: {}, {}",
            StructuredArguments.keyValue("failed", journalforingResult.failed),
            StructuredArguments.keyValue("updated", journalforingResult.updated),
        )
        return journalforingResult
    }

    private fun delays(): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(2).toMillis()
        val intervalDelay = Duration.ofMinutes(60).toMillis()
        return Pair(initialDelay, intervalDelay)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteVarselJournalforingCronjob::class.java)
    }
}
