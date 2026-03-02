package no.nav.syfo.infrastructure.cronjob.dialogmoteOutdated

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.application.IMoteRepository
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.latest
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_OUTDATED_DIALOGMOTE_FAIL
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_OUTDATED_DIALOGMOTE_UPDATE
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjob
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import no.nav.syfo.infrastructure.database.DatabaseInterface
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DialogmoteOutdatedCronjob(
    val dialogmotestatusService: DialogmotestatusService,
    val database: DatabaseInterface,
    val moteRepository: IMoteRepository,
    val outdatedDialogmoterCutoffMonths: Int,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 60 * 24

    override suspend fun run() {
        val outdatedResult = dialogmoteOutdatedJob()

        COUNT_CRONJOB_OUTDATED_DIALOGMOTE_UPDATE.increment(outdatedResult.updated.toDouble())
        COUNT_CRONJOB_OUTDATED_DIALOGMOTE_FAIL.increment(outdatedResult.failed.toDouble())

        log.info(
            "Completed outdated processing with result: {}, {}",
            StructuredArguments.keyValue("failed", outdatedResult.failed),
            StructuredArguments.keyValue("updated", outdatedResult.updated),
        )
    }

    suspend fun dialogmoteOutdatedJob(): DialogmoteCronjobResult {
        val outdatedResult = DialogmoteCronjobResult()
        val cutoff = LocalDate.now()
            .minusMonths(outdatedDialogmoterCutoffMonths.toLong())
            .atStartOfDay()

        log.info("DialogmoteOutdatedCronjob started with cutoff of $outdatedDialogmoterCutoffMonths months, $cutoff")

        val outdatedMoter = moteRepository.findOutdatedMoter(cutoff).toMutableList()
        // moter som skal lukkes adhoc (basert på hardkodet liste med uuid'er)
        log.info("Cronjob for outdated moter found count: ${outdatedMoter.size}")
        for (mote in outdatedMoter) {
            try {
                val motetidspunkt = mote.tidStedList.latest()?.tid
                log.info("Found outdated mote: ${mote.uuid} with status ${mote.status} and moteTidspunkt: $motetidspunkt")
                database.connection.use { connection ->
                    dialogmotestatusService.updateMoteStatus(
                        connection = connection,
                        dialogmote = mote,
                        newDialogmoteStatus = Dialogmote.Status.LUKKET,
                        opprettetAv = "system",
                    )
                    connection.commit()
                }
                outdatedResult.updated++
            } catch (exc: Exception) {
                outdatedResult.failed++
                log.error("Got exception when setting outdated status from cronjob", exc)
            }
        }
        return outdatedResult
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)
    }
}
