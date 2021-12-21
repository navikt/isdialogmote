package no.nav.syfo.cronjob.journalforing

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
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
    private val eregClient: EregClient,
) : DialogmoteCronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 20

    override suspend fun run() {
        val journalforingResult = DialogmoteCronjobResult()

        dialogmoteArbeidstakerVarselJournalforingJob(journalforingResult)
        dialogmoteArbeidsgiverVarselJournalforingJob(journalforingResult)
        dialogmoteBehandlerVarselJournalforingJob(journalforingResult)
        referatJournalforingJob(journalforingResult)

        COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE.increment(journalforingResult.updated.toDouble())
        COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL.increment(journalforingResult.failed.toDouble())

        log.info(
            "Completed journalforing with result: {}, {}",
            StructuredArguments.keyValue("failed", journalforingResult.failed),
            StructuredArguments.keyValue("updated", journalforingResult.updated),
        )
    }

    suspend fun dialogmoteArbeidstakerVarselJournalforingJob(
        journalforingResult: DialogmoteCronjobResult
    ) {
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
                    dialogmotedeltakerVarselJournalpostService.updateArbeidstakerVarselJournalpostId(
                        arbeidstakerVarsel,
                        it,
                    )
                    journalforingResult.updated++
                } ?: throw RuntimeException("Failed to Journalfor ArbeidstakerVarsel: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidstakerVarsel", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun dialogmoteArbeidsgiverVarselJournalforingJob(
        journalforingResult: DialogmoteCronjobResult
    ) {
        val arbeidsgiverVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidsgiverVarselForJournalforingList()
        arbeidsgiverVarselForJournalforingList.forEach { (virksomhetsnummer, personIdent, arbeidsgiverVarsel) ->
            try {
                val virksomhetsnavn = eregClient.organisasjonVirksomhetsnavn(virksomhetsnummer)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = arbeidsgiverVarsel.toJournalpostRequest(
                        brukerPersonIdent = personIdent,
                        virksomhetsnummer = virksomhetsnummer,
                        virksomhetsnavn = virksomhetsnavn?.virksomhetsnavn ?: "",
                    ),
                )?.journalpostId

                journalpostId?.let { it ->
                    dialogmotedeltakerVarselJournalpostService.updateArbeidsgiverVarselJournalpostId(
                        arbeidsgiverVarsel,
                        it,
                    )
                    journalforingResult.updated++
                } ?: throw RuntimeException("Failed to Journalfor ArbeidsgiverVarsel: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidsgiverVarsel", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun dialogmoteBehandlerVarselJournalforingJob(
        journalforingResult: DialogmoteCronjobResult
    ) {
        val behandlerVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerBehandlerVarselForJournalforingList()
        behandlerVarselForJournalforingList.forEach { (personIdent, behandler, behandlerVarsel) ->
            try {
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = behandlerVarsel.toJournalpostRequest(
                        brukerPersonIdent = personIdent,
                        behandlerPersonIdent = behandler.personIdent,
                        behandlerNavn = behandler.behandlerNavn,
                    ),
                )?.journalpostId

                journalpostId?.let { it ->
                    dialogmotedeltakerVarselJournalpostService.updateBehandlerVarselJournalpostId(
                        behandlerVarsel,
                        it,
                    )
                    journalforingResult.updated++
                } ?: throw RuntimeException("Failed to Journalfor BehandlerVarsel: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of BehandlerVarsel", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun referatJournalforingJob(
        journalforingResult: DialogmoteCronjobResult
    ) {
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
                } ?: throw RuntimeException("Failed to Journalfor Referat: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteVarselJournalforingCronjob::class.java)
    }
}
