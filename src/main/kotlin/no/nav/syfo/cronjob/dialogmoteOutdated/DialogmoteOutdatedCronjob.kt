package no.nav.syfo.cronjob.dialogmoteOutdated

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.cronjob.*
import no.nav.syfo.dialogmote.DialogmotestatusService
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DialogmoteOutdatedCronjob(
    dialogmotestatusService: DialogmotestatusService,
    database: DatabaseInterface,
    outdatedDialogmoterCutoff: LocalDate,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 60 * 24

    override suspend fun run() {
        val outdatedResult = DialogmoteCronjobResult()

        dialogmoteOutdatedJob(outdatedResult)

        COUNT_CRONJOB_OUTDATED_DIALOGMOTE_UPDATE.increment(outdatedResult.updated.toDouble())
        COUNT_CRONJOB_OUTDATED_DIALOGMOTE_FAIL.increment(outdatedResult.failed.toDouble())

        log.info(
            "Completed outdated processing with result: {}, {}",
            StructuredArguments.keyValue("failed", outdatedResult.failed),
            StructuredArguments.keyValue("updated", outdatedResult.updated),
        )
    }

    fun dialogmoteOutdatedJob(
        outdatedResult: DialogmoteCronjobResult,
    ) {
        // TODO:
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)
    }
}
