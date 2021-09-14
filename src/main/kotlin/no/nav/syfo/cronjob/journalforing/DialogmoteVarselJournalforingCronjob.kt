package no.nav.syfo.cronjob.journalforing

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE
import no.nav.syfo.cronjob.DialogmoteCronjob
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.dialogmote.ReferatJournalpostService
import no.nav.syfo.dialogmote.domain.*
import org.slf4j.LoggerFactory

class DialogmoteVarselJournalforingCronjob(
    private val dialogmotedeltakerVarselJournalpostService: DialogmotedeltakerVarselJournalpostService,
    private val referatJournalpostService: ReferatJournalpostService,
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 60

    override suspend fun run() {
        dialogmoteVarselJournalforingJob()
        referatJournalforingJob()
    }

    suspend fun dialogmoteVarselJournalforingJob(): DialogmoteCronjobResult {
        val journalforingResult = DialogmoteCronjobResult()

        val arbeidstakerVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidstakerVarselForJournalforingList()
        arbeidstakerVarselForJournalforingList.forEach { (personIdent, arbeidstakerVarsel) ->
            try {
                val navn = pdlClient.navn(personIdent)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = arbeidstakerVarsel.toJournalpostRequest(
                        personIdent = personIdent,
                        navn = navn,
                    ),
                )?.journalpostId

                journalpostId?.let { it ->
                    dialogmotedeltakerVarselJournalpostService.updateJournalpostId(
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

    suspend fun referatJournalforingJob(): DialogmoteCronjobResult {
        val journalforingResult = DialogmoteCronjobResult()

        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingList()
        referatList.forEach { (personIdentNumber, referat) ->
            try {
                val navn = pdlClient.navn(personIdentNumber)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = referat.toJournalforingRequest(
                        personIdent = personIdentNumber,
                        navn = navn,
                    )
                )?.journalpostId

                journalpostId?.let { it ->
                    referatJournalpostService.updateJournalpostIdForReferat(
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
