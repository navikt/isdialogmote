package no.nav.syfo.infrastructure.cronjob.journalpostdistribusjon

import java.util.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjob
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.infrastructure.database.dialogmote.ReferatJournalpostService
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import org.slf4j.LoggerFactory

class DialogmoteJournalpostDistribusjonCronjob(
    private val dialogmotedeltakerVarselJournalpostService: DialogmotedeltakerVarselJournalpostService,
    private val referatJournalpostService: ReferatJournalpostService,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 60

    override suspend fun run() {
        dialogmoteVarselJournalpostDistribusjon()
        referatJournalpostDistribusjon()
    }

    fun dialogmoteVarselJournalpostDistribusjon(): DialogmoteCronjobResult {
        val result = DialogmoteCronjobResult()
        dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidstakerVarselForJournalpostDistribusjonList()
            .forEach { (arbeidstakerFnr, arbeidstakerVarsel, motetidspunkt) ->
                try {
                    log.info("ArbeidstakerVarsel-journalpost-distribusjon til reserverte via esyfovarsel. About to send varsel of type: ${arbeidstakerVarsel.varselType}")
                    arbeidstakerVarselService.sendVarsel(
                        arbeidstakerVarsel.varselType,
                        arbeidstakerFnr,
                        UUID.randomUUID(),
                        arbeidstakerVarsel.journalpostId!!,
                        motetidspunkt
                    )
                    dialogmotedeltakerVarselJournalpostService.updateBrevBestilt(dialogmotedeltakerArbeidstakerVarsel = arbeidstakerVarsel)
                    result.updated++
                    COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE.increment()
                } catch (e: Exception) {
                    log.error("Exception caught in ArbeidstakerVarsel-journalpost-distribusjon", e)
                    result.failed++
                    COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL.increment()
                }
            }
        log.info(
            "Completed ArbeidstakerVarsel-journalpost-distribusjon with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    fun referatJournalpostDistribusjon(): DialogmoteCronjobResult {
        val result = DialogmoteCronjobResult()
        referatJournalpostService.getDialogmoteReferatForJournalpostDistribusjonList()
            .forEach { (referatId, personIdent, referatJournalpostId, motetidspunkt) ->
                try {
                    log.info("ArbeidstakerVarsel-journalpost-distribusjon til reserverte via esyfovarsel. About to send varsel of type: ${MotedeltakerVarselType.REFERAT}")
                    arbeidstakerVarselService.sendVarsel(
                        MotedeltakerVarselType.REFERAT,
                        personIdent,
                        UUID.randomUUID(),
                        referatJournalpostId!!,
                        motetidspunkt
                    )
                    referatJournalpostService.updateBrevBestilt(referatId = referatId)
                    result.updated++
                    COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE.increment()
                } catch (e: Exception) {
                    log.error("Exception caught in Referat-journalpost-distribusjon", e)
                    result.failed++
                    COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL.increment()
                }
            }
        log.info(
            "Completed Referat-journalpost-distribusjon with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteJournalpostDistribusjonCronjob::class.java)
    }
}
