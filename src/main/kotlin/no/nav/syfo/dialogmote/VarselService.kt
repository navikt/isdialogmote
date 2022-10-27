package no.nav.syfo.dialogmote

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
import java.time.LocalDateTime
import java.util.*

class VarselService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val behandlerVarselService: BehandlerVarselService,
    private val altinnClient: AltinnClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val isAltinnSendingEnabled: Boolean,
) {

    suspend fun sendVarsel(
        tidspunktForVarsel: LocalDateTime,
        varselType: MotedeltakerVarselType,
        isDigitalVarselEnabledForArbeidstaker: Boolean,
        arbeidstakerPersonIdent: PersonIdent,
        arbeidstakernavn: String,
        arbeidstakerId: UUID,
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
        token: String,
        callId: String,
    ) {
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
            )
        }

        if (isDigitalVarselEnabledForArbeidstaker) {
            arbeidstakerVarselService.sendVarsel(
                createdAt = tidspunktForVarsel,
                personIdent = arbeidstakerPersonIdent,
                type = varselType,
                motedeltakerArbeidstakerUuid = arbeidstakerId,
                varselUuid = arbeidstakerbrevId,
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
