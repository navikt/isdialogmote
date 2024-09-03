package no.nav.syfo.cronjob.journalforing

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL
import no.nav.syfo.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE
import no.nav.syfo.cronjob.DialogmoteCronjob
import no.nav.syfo.cronjob.DialogmoteCronjobResult
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.domain.*
import org.slf4j.LoggerFactory

class DialogmoteVarselJournalforingCronjob(
    private val dialogmotedeltakerVarselJournalpostService: DialogmotedeltakerVarselJournalpostService,
    private val referatJournalpostService: ReferatJournalpostService,
    private val pdfService: PdfService,
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
        referatJournalforingJobArbeidstaker(journalforingResult)
        referatJournalforingJobArbeidsgiver(journalforingResult)
        referatJournalforingJobBehandler(journalforingResult)

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
                val pdf = pdfService.getPdf(arbeidstakerVarsel.pdfId)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = arbeidstakerVarsel.toJournalpostRequest(
                        personIdent = personIdent,
                        navn = navn,
                        pdf = pdf,
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
                log.error("Exception caught while attempting Journalforing of ArbeidstakerVarsel, will try again later", e)
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
                val pdf = pdfService.getPdf(arbeidsgiverVarsel.pdfId)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = arbeidsgiverVarsel.toJournalpostRequest(
                        brukerPersonIdent = personIdent,
                        virksomhetsnummer = virksomhetsnummer,
                        virksomhetsnavn = virksomhetsnavn?.virksomhetsnavn ?: "",
                        pdf = pdf,
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
                val pdf = pdfService.getPdf(behandlerVarsel.pdfId)
                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = behandlerVarsel.toJournalpostRequest(
                        brukerPersonIdent = personIdent,
                        behandlerPersonIdent = behandler.personIdent,
                        behandlerNavn = behandler.behandlerNavn,
                        pdf = pdf,
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

    suspend fun referatJournalforingJobArbeidstaker(
        journalforingResult: DialogmoteCronjobResult
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListArbeidstaker()
        referatList.forEach { (personIdent, referat) ->
            try {
                val navn = pdlClient.navn(personIdent)
                val pdf = pdfService.getPdf(referat.pdfId!!)
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val journalpostRequest = referat.toJournalforingRequestArbeidstaker(
                    personIdent = personIdent,
                    navn = navn,
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to arbeidstaker with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId}")
                val journalpostId = dokarkivClient.journalfor(journalpostRequest)?.journalpostId

                journalpostId?.let { it ->
                    referatJournalpostService.updateJournalpostIdArbeidstakerForReferat(
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

    suspend fun referatJournalforingJobArbeidsgiver(
        journalforingResult: DialogmoteCronjobResult
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListArbeidsgiver()
        referatList.forEach { (virksomhetsnummer, personIdent, referat) ->
            try {
                val virksomhetsnavn = eregClient.organisasjonVirksomhetsnavn(virksomhetsnummer)
                val pdf = pdfService.getPdf(referat.pdfId!!)
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val journalpostRequest = referat.toJournalforingRequestArbeidsgiver(
                    brukerPersonIdent = personIdent,
                    virksomhetsnummer = virksomhetsnummer,
                    virksomhetsnavn = virksomhetsnavn?.virksomhetsnavn ?: "",
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to arbeidsgiver with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId}")
                val journalpostId = dokarkivClient.journalfor(journalpostRequest)?.journalpostId

                journalpostId?.let { it ->
                    referatJournalpostService.updateJournalpostIdArbeidsgiverForReferat(
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

    suspend fun referatJournalforingJobBehandler(
        journalforingResult: DialogmoteCronjobResult
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListBehandler()
        referatList.forEach { (personIdent, behandler, referat) ->
            try {
                val pdf = pdfService.getPdf(referat.pdfId!!)
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val journalpostRequest = referat.toJournalforingRequestBehandler(
                    brukerPersonIdent = personIdent,
                    behandlerPersonIdent = behandler.personIdent,
                    behandlerNavn = behandler.behandlerNavn,
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to behandler with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId}")
                val journalpostId = dokarkivClient.journalfor(journalpostRequest)?.journalpostId

                journalpostId?.let { it ->
                    referatJournalpostService.updateJournalpostIdBehandlerForReferat(
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
