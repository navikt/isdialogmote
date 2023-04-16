package no.nav.syfo.cronjob.journalpostdistribusjon

import java.util.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE
import no.nav.syfo.cronjob.DialogmoteCronjob
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.dialogmote.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.dialogmote.ReferatJournalpostService
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import org.slf4j.LoggerFactory

class DialogmoteJournalpostDistribusjonCronjob(
    private val dialogmotedeltakerVarselJournalpostService: DialogmotedeltakerVarselJournalpostService,
    private val referatJournalpostService: ReferatJournalpostService,
    private val journalpostdistribusjonClient: JournalpostdistribusjonClient,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val isSendingToReservedViaEsyfovarselEnabled: Boolean,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 60

    override suspend fun run() {
        dialogmoteVarselJournalpostDistribusjon()
        referatJournalpostDistribusjon()
    }

    suspend fun dialogmoteVarselJournalpostDistribusjon(): DialogmoteCronjobResult {
        val result = DialogmoteCronjobResult()
        dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidstakerVarselForJournalpostDistribusjonList()
            .forEach { arbeidstakerVarselPair ->
                val arbeidstakerFnr = arbeidstakerVarselPair.first
                val arbeidstakerVarsel = arbeidstakerVarselPair.second
                try {
                    if (isSendingToReservedViaEsyfovarselEnabled) {
                        if (arbeidstakerVarsel.journalpostId !== null) {
                            log.info("ArbeidstakerVarsel-journalpost-distribusjon til reserverte via esyfovarsel ENABLED. About to send varsel of type: ${arbeidstakerVarsel.varselType}")
                            arbeidstakerVarselService.sendVarsel(arbeidstakerVarsel.varselType, arbeidstakerFnr, UUID.randomUUID(), arbeidstakerVarsel.journalpostId)
                            dialogmotedeltakerVarselJournalpostService.updateBestilling(
                                // Oppdaterer utsendingstidspunkt
                                dialogmotedeltakerArbeidstakerVarsel = arbeidstakerVarsel,
                                bestillingsId = null,
                            )
                        }
                    } else {
                        log.info("ArbeidstakerVarsel-journalpost-distribusjon til reserverte via esyfovarsel DISABLED. About to send varsel of type: ${arbeidstakerVarsel.varselType}")
                        val bestillingsId =
                            journalpostdistribusjonClient.distribuerJournalpost(arbeidstakerVarsel.journalpostId!!)?.bestillingsId
                        dialogmotedeltakerVarselJournalpostService.updateBestilling(
                            dialogmotedeltakerArbeidstakerVarsel = arbeidstakerVarsel,
                            bestillingsId = bestillingsId,
                        )
                    }
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

    suspend fun referatJournalpostDistribusjon(): DialogmoteCronjobResult {
        val result = DialogmoteCronjobResult()
        referatJournalpostService.getDialogmoteReferatForJournalpostDistribusjonList()
            .forEach { (referatId, personIdent, referatJournalpostId) ->
                try {
                    if (isSendingToReservedViaEsyfovarselEnabled) {
                        arbeidstakerVarselService.sendVarsel(MotedeltakerVarselType.REFERAT, personIdent, UUID.randomUUID(), referatJournalpostId!!)
                    } else {
                        val bestillingsId =
                            journalpostdistribusjonClient.distribuerJournalpost(referatJournalpostId!!)?.bestillingsId
                        referatJournalpostService.updateBestillingsId(
                            referatId = referatId,
                            bestillingsId = bestillingsId,
                        )
                    }
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
