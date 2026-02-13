package no.nav.syfo.infrastructure.cronjob.dialogmoteOutdated

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.application.DialogmoterelasjonService
import no.nav.syfo.application.DialogmotestatusService
import no.nav.syfo.infrastructure.database.findOutdatedMoter
import no.nav.syfo.infrastructure.database.getDialogmote
import no.nav.syfo.domain.dialogmote.latest
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_OUTDATED_DIALOGMOTE_FAIL
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_OUTDATED_DIALOGMOTE_UPDATE
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjob
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class DialogmoteOutdatedCronjob(
    val dialogmotestatusService: DialogmotestatusService,
    val dialogmoterelasjonService: DialogmoterelasjonService,
    val database: DatabaseInterface,
    val outdatedDialogmoterCutoffMonths: Int,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 1440 // 24 hours

    override suspend fun run() {
        log.info("DialogmoteOutdatedCronjob started with cutoff of $outdatedDialogmoterCutoffMonths months")

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

        val moteListe = database.findOutdatedMoter(cutoff).toMutableList()
        // moter som skal lukkes adhoc (basert på hardkodet liste med uuid'er)
        uuids.forEach { uuid -> moteListe.addAll(database.getDialogmote(UUID.fromString(uuid))) }
        moteListe.retainAll { pDialogmote ->
            pDialogmote.status == Dialogmote.Status.INNKALT.name || pDialogmote.status == Dialogmote.Status.NYTT_TID_STED.name
        }
        val dialogmoteList = moteListe.map { dialogmoterelasjonService.extendDialogmoteRelations(it) }
        log.info("Cronjob for outdated moter found count: ${dialogmoteList.size}")
        for (mote in dialogmoteList) {
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
        private val uuids = listOf<String>("4348a3c5-6795-4402-971c-b1599d114096")
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)
    }
}
