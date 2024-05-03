package no.nav.syfo.cronjob.dialogmoteOutdated

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.cronjob.*
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.findOutdatedMoter
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.latest
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class DialogmoteOutdatedCronjob(
    val dialogmotestatusService: DialogmotestatusService,
    val dialogmoterelasjonService: DialogmoterelasjonService,
    val database: DatabaseInterface,
    val outdatedDialogmoterCutoff: LocalDate,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 4
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
        val moteListe = database.findOutdatedMoter(cutoff)
        // moter som skal lukkes adhoc (basert på hardkodet liste med uuid'er)
        uuids.forEach { uuid -> moteListe.addAll(database.getDialogmote(UUID.fromString(uuid))) }
        moteListe.retainAll { pDialogmote ->
            pDialogmote.status == DialogmoteStatus.INNKALT.name || pDialogmote.status == DialogmoteStatus.NYTT_TID_STED.name
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
        private val uuids = listOf("7a7e5065-9309-48f1-9c8c-13fc803f0b7d")
        private val log = LoggerFactory.getLogger(DialogmoteOutdatedCronjob::class.java)
    }
}
