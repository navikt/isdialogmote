package no.nav.syfo.infrastructure.cronjob.journalforing

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.DialogmotedeltakerVarselJournalpostService
import no.nav.syfo.application.IPdfRepository
import no.nav.syfo.application.ReferatJournalpostService
import no.nav.syfo.domain.dialogmote.toJournalforingRequestArbeidsgiver
import no.nav.syfo.domain.dialogmote.toJournalforingRequestArbeidstaker
import no.nav.syfo.domain.dialogmote.toJournalforingRequestBehandler
import no.nav.syfo.domain.dialogmote.toJournalpostRequest
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.ereg.EregClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL
import no.nav.syfo.infrastructure.cronjob.COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjob
import no.nav.syfo.infrastructure.cronjob.DialogmoteCronjobResult
import org.slf4j.LoggerFactory
import java.util.*

class DialogmoteVarselJournalforingCronjob(
    private val dialogmotedeltakerVarselJournalpostService: DialogmotedeltakerVarselJournalpostService,
    private val referatJournalpostService: ReferatJournalpostService,
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
    private val eregClient: EregClient,
    private val dialogmeldingClient: DialogmeldingClient,
    private val isJournalforingRetryEnabled: Boolean,
    private val pdfRepository: IPdfRepository,
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
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val arbeidstakerVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidstakerVarselForJournalforingList()
        arbeidstakerVarselForJournalforingList.forEach { (personIdent, arbeidstakerVarsel) ->
            try {
                val navn = pdlClient.navn(personIdent)
                val pdf = pdfRepository.getPdf(arbeidstakerVarsel.pdfId).pdf
                val journalpostRequest = arbeidstakerVarsel.toJournalpostRequest(
                    personIdent = personIdent,
                    navn = navn,
                    pdf = pdf,
                )
                val journalpostId = journalfor(journalpostRequest)
                dialogmotedeltakerVarselJournalpostService.updateArbeidstakerVarselJournalpostId(
                    arbeidstakerVarsel,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidstakerVarsel, will try again later", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun dialogmoteArbeidsgiverVarselJournalforingJob(
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val arbeidsgiverVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerArbeidsgiverVarselForJournalforingList()
        arbeidsgiverVarselForJournalforingList.forEach { (virksomhetsnummer, personIdent, arbeidsgiverVarsel) ->
            try {
                val virksomhetsnavn = eregClient.organisasjonVirksomhetsnavn(virksomhetsnummer)
                val pdf = pdfRepository.getPdf(arbeidsgiverVarsel.pdfId).pdf
                val journalpostRequest = arbeidsgiverVarsel.toJournalpostRequest(
                    brukerPersonIdent = personIdent,
                    virksomhetsnummer = virksomhetsnummer,
                    virksomhetsnavn = virksomhetsnavn?.virksomhetsnavn ?: "",
                    pdf = pdf,
                )
                val journalpostId = journalfor(journalpostRequest)
                dialogmotedeltakerVarselJournalpostService.updateArbeidsgiverVarselJournalpostId(
                    arbeidsgiverVarsel,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of ArbeidsgiverVarsel", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun dialogmoteBehandlerVarselJournalforingJob(
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val behandlerVarselForJournalforingList =
            dialogmotedeltakerVarselJournalpostService.getDialogmotedeltakerBehandlerVarselForJournalforingList()
        behandlerVarselForJournalforingList.forEach { (personIdent, behandler, behandlerVarsel) ->
            try {
                val pdf = pdfRepository.getPdf(behandlerVarsel.pdfId).pdf
                val behandlerDTO = dialogmeldingClient.getBehandler(UUID.fromString(behandler.behandlerRef))
                val journalpostRequest = behandlerVarsel.toJournalpostRequest(
                    brukerPersonIdent = personIdent,
                    behandlerPersonIdent = behandler.personIdent,
                    behandlerHprId = behandlerDTO?.hprId,
                    behandlerNavn = behandler.behandlerNavn,
                    pdf = pdf,
                )
                val journalpostId = journalfor(journalpostRequest)
                dialogmotedeltakerVarselJournalpostService.updateBehandlerVarselJournalpostId(
                    behandlerVarsel,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of BehandlerVarsel", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun referatJournalforingJobArbeidstaker(
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListArbeidstaker()
        referatList.forEach { (personIdent, referat) ->
            try {
                val navn = pdlClient.navn(personIdent)
                val pdf = pdfRepository.getPdf(referat.pdfId!!).pdf
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val journalpostRequest = referat.toJournalforingRequestArbeidstaker(
                    personIdent = personIdent,
                    navn = navn,
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to arbeidstaker with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId}")
                val journalpostId = journalfor(journalpostRequest)
                referatJournalpostService.updateJournalpostIdArbeidstakerForReferat(
                    referat,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun referatJournalforingJobArbeidsgiver(
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListArbeidsgiver()
        referatList.forEach { (virksomhetsnummer, personIdent, referat) ->
            try {
                val virksomhetsnavn = eregClient.organisasjonVirksomhetsnavn(virksomhetsnummer)
                val pdf = pdfRepository.getPdf(referat.pdfId!!).pdf
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val journalpostRequest = referat.toJournalforingRequestArbeidsgiver(
                    brukerPersonIdent = personIdent,
                    virksomhetsnummer = virksomhetsnummer,
                    virksomhetsnavn = virksomhetsnavn?.virksomhetsnavn ?: "",
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to arbeidsgiver with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId}")
                val journalpostId = journalfor(journalpostRequest)
                referatJournalpostService.updateJournalpostIdArbeidsgiverForReferat(
                    referat,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
            }
        }
    }

    suspend fun referatJournalforingJobBehandler(
        journalforingResult: DialogmoteCronjobResult,
    ) {
        val referatList = referatJournalpostService.getDialogmoteReferatJournalforingListBehandler()
        referatList.forEach { (personIdent, behandler, referat) ->
            try {
                val pdf = pdfRepository.getPdf(referat.pdfId!!).pdf
                val moteTidspunkt = referatJournalpostService.getMotetidspunkt(referat.moteId)
                val behandlerDTO = dialogmeldingClient.getBehandler(UUID.fromString(behandler.behandlerRef))
                val journalpostRequest = referat.toJournalforingRequestBehandler(
                    brukerPersonIdent = personIdent,
                    behandlerPersonIdent = behandler.personIdent,
                    behandlerHprId = behandlerDTO?.hprId,
                    behandlerNavn = behandler.behandlerNavn,
                    pdf = pdf,
                    moteTidspunkt = moteTidspunkt,
                )
                log.info("Journalfør referat to behandler with uuid ${referat.uuid} and eksternReferanseId: ${journalpostRequest.eksternReferanseId} using hprid ${behandlerDTO?.hprId != null}")
                val journalpostId = journalfor(journalpostRequest)
                referatJournalpostService.updateJournalpostIdBehandlerForReferat(
                    referat,
                    journalpostId,
                )
                journalforingResult.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of Referat", e)
                journalforingResult.failed++
            }
        }
    }

    private suspend fun journalfor(journalpostRequest: JournalpostRequest): Int {
        val journalpostResponse = dokarkivClient.journalfor(journalpostRequest)
        return if (journalpostResponse?.journalpostId == null) {
            if (isJournalforingRetryEnabled) {
                throw RuntimeException("Failed journalforing: response missing journalpostId")
            } else {
                log.error("Journalforing failed, skipping retry (should only happen in dev-gcp)")
                // Defaulting'en til DEFAULT_FAILED_JP_ID skal bare forekomme i dev-gcp:
                // Har dette fordi vi ellers spammer ned dokarkiv med forsøk på å journalføre
                // på personer som mangler aktør-id.
                DEFAULT_FAILED_JP_ID
            }
        } else {
            journalpostResponse.journalpostId
        }
    }

    companion object {
        private const val DEFAULT_FAILED_JP_ID = 0
        private val log = LoggerFactory.getLogger(DialogmoteVarselJournalforingCronjob::class.java)
    }
}
