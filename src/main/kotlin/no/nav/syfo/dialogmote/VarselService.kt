package no.nav.syfo.dialogmote

import java.util.*
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.altinn.createAltinnMelding
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.oppfolgingstilfelle.isInactive
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class VarselService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val behandlerVarselService: BehandlerVarselService,
    private val altinnClient: AltinnClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val isAltinnSendingEnabled: Boolean,
) {
    private val log: Logger = LoggerFactory.getLogger(VarselService::class.java)

    suspend fun sendVarsel(
        varselType: MotedeltakerVarselType,
        isDigitalVarselEnabledForArbeidstaker: Boolean,
        arbeidstakerPersonIdent: PersonIdent,
        arbeidstakernavn: String,
        arbeidstakerbrevId: UUID,
        narmesteLeder: NarmesteLederRelasjonDTO?,
        virksomhetsbrevId: UUID,
        virksomhetsPdf: ByteArray,
        virksomhetsnummer: Virksomhetsnummer,
        skalVarsleBehandler: Boolean = true,
        behandlerId: Int?,
        behandlerRef: String?,
        behandlerDocument: List<DocumentComponentDTO>,
        behandlerPdf: ByteArray?,
        behandlerbrevId: UUID?,
        behandlerbrevParentId: String?,
        behandlerInnkallingUuid: UUID?,
        motetidspunkt: LocalDateTime?,
        token: String,
        callId: String,
    ) {
        //TODO:remove
        log.info("MF: Skal sende varsel ${varselType.name} til $arbeidstakerPersonIdent")
        val altinnMelding = createAltinnMelding(
            virksomhetsbrevId,
            virksomhetsnummer,
            virksomhetsPdf,
            varselType,
            arbeidstakerPersonIdent,
            arbeidstakernavn,
            narmesteLeder != null,
        )

        val tilfelle = oppfolgingstilfelleClient.oppfolgingstilfellePerson(
            callId = callId,
            personIdent = arbeidstakerPersonIdent,
            token = token,
        )

        val hasActiveTilfelle = tilfelle != null && !tilfelle.isInactive()

        if (isAltinnSendingEnabled) {
            altinnClient.sendToVirksomhet(
                altinnMelding = altinnMelding,
            )
        }

        if (narmesteLeder != null && hasActiveTilfelle) {
            narmesteLederVarselService.sendVarsel(
                narmesteLeder = narmesteLeder,
                varseltype = varselType,
                motetidspunkt = motetidspunkt
            )
        }
        if (isDigitalVarselEnabledForArbeidstaker) {
            log.info("Skal varsle bruker digitalt om $varselType")
            arbeidstakerVarselService.sendVarsel(
                varseltype = varselType,
                personIdent = arbeidstakerPersonIdent,
                varselUuid = arbeidstakerbrevId,
                journalpostId = null,
                motetidspunkt = motetidspunkt
            )
        }

        if (skalVarsleBehandler) {
            behandlerId?.let {
                behandlerVarselService.sendVarsel(
                    behandlerRef = behandlerRef!!,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    document = behandlerDocument,
                    pdf = behandlerPdf!!,
                    varseltype = varselType,
                    varselUuid = behandlerbrevId!!,
                    varselParentId = behandlerbrevParentId,
                    varselInnkallingUuid = behandlerInnkallingUuid,
                )
            }
        }
    }
}
