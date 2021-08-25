package no.nav.syfo.cronjob.journalforing

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE
import no.nav.syfo.cronjob.DialogmoteCronjob
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalforingService
import no.nav.syfo.dialogmote.ReferatJournalforingService
import no.nav.syfo.dialogmote.domain.*
import org.slf4j.LoggerFactory

data class JournalforingResult(
    var updated: Int = 0,
    var failed: Int = 0,
)

class DialogmoteVarselJournalforingCronjob(
    private val dialogmotedeltakerVarselJournalforingService: DialogmotedeltakerVarselJournalforingService,
    private val referatJournalforingService: ReferatJournalforingService,
    private val dokarkivClient: DokarkivClient,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 60

    override suspend fun run() {
        dialogmoteVarselJournalforingJob()
        referatJournalforingJob()
    }

    suspend fun dialogmoteVarselJournalforingJob(): JournalforingResult {
        val journalforingResult = JournalforingResult()

        val arbeidstakerVarselForJournalforingList =
            dialogmotedeltakerVarselJournalforingService.getDialogmotedeltakerArbeidstakerVarselForJournalforingList()
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
                    COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE.increment()
                } ?: throw RuntimeException("Failed to Journalfor ArbeidstakerVarsel: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidstakerVarsel", e)
                journalforingResult.failed++
                COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL.increment()
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
                    COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE.increment()
                } ?: throw RuntimeException("Failed to Journalfor Referat: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
                COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL.increment()
            }
        }
        log.info(
            "Completed referat-journalforing with result: {}, {}",
            StructuredArguments.keyValue("failed", journalforingResult.failed),
            StructuredArguments.keyValue("updated", journalforingResult.updated),
        )
        return journalforingResult
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteVarselJournalforingCronjob::class.java)
    }
}
