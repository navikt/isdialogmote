package no.nav.syfo.cronjob.statusendring

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cronjob.*
import org.slf4j.LoggerFactory

data class PublishDialogmoteStatusEndringResult(
    var updated: Int = 0,
    var failed: Int = 0,
)

class PublishDialogmoteStatusEndringCronjob(
    private val publishDialogmoteStatusEndringService: PublishDialogmoteStatusEndringService,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 60

    override suspend fun run() {
        dialogmoteStatusEndringPublishJob()
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
                COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE.increment()
            } catch (e: Exception) {
                log.error("Exception caught while attempting to publish DialogmoteStatusEndret", e)
                result.failed++
                COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_FAIL.increment()
            }
        }
        log.info(
            "Completed dialogmoteStatusEndring-publishing with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishDialogmoteStatusEndringCronjob::class.java)
    }
}
