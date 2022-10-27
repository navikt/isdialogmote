package no.nav.syfo.cronjob.dialogmoteOutdated

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.cronjob.*
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.findOutdatedMoter
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.latest
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DialogmoteOutdatedCronjob(
    val dialogmotestatusService: DialogmotestatusService,
    val dialogmoterelasjonService: DialogmoterelasjonService,
    val database: DatabaseInterface,
    val outdatedDialogmoterCutoff: LocalDate,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 240

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

    suspend fun dialogmoteOutdatedJob(
        outdatedResult: DialogmoteCronjobResult,
    ) {
        val cutoff = outdatedDialogmoterCutoff.atStartOfDay()
        val moteListe = database.findOutdatedMoter(cutoff).map { dialogmoterelasjonService.extendDialogmoteRelations(it) }
        log.info("Cronjob for outdated moter found count: ${moteListe.size}")
        for (mote in moteListe) {
            try {
                val motetidspunkt = mote.tidStedList.latest()?.tid
                log.info("Found outdated mote: ${mote.uuid} with status ${mote.status} and moteTidspunkt: $motetidspunkt")
                database.connection.use { connection ->
                    dialogmotestatusService.updateMoteStatus(
                        connection = connection,
                        dialogmote = mote,
                        newDialogmoteStatus = DialogmoteStatus.LUKKET,
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
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)
    }
}
