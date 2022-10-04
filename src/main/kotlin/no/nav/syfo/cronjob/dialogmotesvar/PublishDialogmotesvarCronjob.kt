package no.nav.syfo.cronjob.dialogmotesvar

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cronjob.*
import org.slf4j.LoggerFactory

class PublishDialogmotesvarCronjob(
    private val publishDialogmotesvarService: PublishDialogmotesvarService,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        dialogmotesvarPublishJob()
    }

    fun dialogmotesvarPublishJob(): DialogmoteCronjobResult {
        val result = DialogmoteCronjobResult()

        val dialogmotesvarList = publishDialogmotesvarService.getUnpublishedDialogmotesvar()
        dialogmotesvarList.forEach { dialogmotesvar ->
            try {
                publishDialogmotesvarService.publishAndUpdateDialogmotesvar(
                    dialogmotesvar = dialogmotesvar,
                )
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting to publish Dialogmotesvar", e)
                result.failed++
            }
        }
        log.info(
            "Completed dialogmotesvar-publishing with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishDialogmotesvarCronjob::class.java)
    }
}
