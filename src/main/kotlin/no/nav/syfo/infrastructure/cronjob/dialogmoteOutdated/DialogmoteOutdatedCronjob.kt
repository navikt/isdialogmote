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
import no.nav.syfo.infrastructure.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob.Companion.uuids
import no.nav.syfo.infrastructure.database.DatabaseInterface
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

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

        val outdatedMoter = moteRepository.findOutdatedMoter(cutoff)
        val moterSomSkalLukkes = outdatedMoter + getAdhocLukkinger()

        log.info("Cronjob for outdated moter found count: ${moterSomSkalLukkes.size}")
        for (mote in moterSomSkalLukkes) {
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

    /**
     * Henter moter som skal lukkes adhoc fra listen [uuids]
     * Lukker bare de som har status INNKALT eller NYTT_TID_STED, for å unngå å lukke moter som allerede er lukket eller avlyst
     *
     * @return List<Dialogmote> med moter som skal lukkes
     */
    private fun getAdhocLukkinger(): List<Dialogmote> =
        uuids.mapNotNull {
            try {
                val mote = moteRepository.getMote(UUID.fromString(it))
                if (mote.status == Dialogmote.Status.INNKALT || mote.status == Dialogmote.Status.NYTT_TID_STED) {
                    mote
                } else {
                    log.error("Mote with uuid $it has wrong status, skipping close")
                    null
                }
            } catch (e: Exception) {
                log.error("Failed to get mote with uuid $it, skipping", e)
                null
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)

        private val uuids = listOf<String>()
    }
}
