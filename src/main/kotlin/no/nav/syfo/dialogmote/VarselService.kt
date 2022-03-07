package no.nav.syfo.dialogmote

import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

class VarselService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val behandlerVarselService: BehandlerVarselService,
    private val altinnClient: AltinnClient,
) {

    fun sendVarsel(
        tidspunktForVarsel: LocalDateTime,
        varselType: MotedeltakerVarselType,
        moteTidspunkt: LocalDateTime,
        isDigitalVarselEnabledForArbeidstaker: Boolean,
        arbeidstakerPersonIdent: PersonIdentNumber,
        arbeidstakerId: UUID,
        arbeidstakerbrevId: UUID,
        narmesteLeder: NarmesteLederDTO?,
        virksomhetsbrevId: UUID,
        virksomhetsPdf: ByteArray,
        virksomhetsnummer: Virksomhetsnummer,
        skalVarsleBehandler: Boolean = true,
        behandlerId: Int?,
        behandlerRef: String?,
        behandlerDocument: List<DocumentComponentDTO>,
        behandlerPdf: ByteArray?,
        behandlerbrevId: Pair<Int, UUID>?,
        behandlerbrevParentId: String?,
        behandlerInnkallingUuid: UUID?,
    ) {
        if (isDigitalVarselEnabledForArbeidstaker) {
            arbeidstakerVarselService.sendVarsel(
                createdAt = tidspunktForVarsel,
                personIdent = arbeidstakerPersonIdent,
                type = varselType,
                motedeltakerArbeidstakerUuid = arbeidstakerId,
                varselUuid = arbeidstakerbrevId,
            )
        }

        if (narmesteLeder != null) {
            narmesteLederVarselService.sendVarsel(
                createdAt = tidspunktForVarsel,
                moteTidspunkt = moteTidspunkt,
                narmesteLeder = narmesteLeder,
                varseltype = varselType
            )
        } else {
            // TODO Gjør dette først
            altinnClient.sendToVirksomhet(virksomhetsbrevId, virksomhetsPdf, virksomhetsnummer)
        }

        if (skalVarsleBehandler) {
            behandlerId?.let {
                behandlerVarselService.sendVarsel(
                    behandlerRef = behandlerRef!!,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    document = behandlerDocument,
                    pdf = behandlerPdf!!,
                    varseltype = varselType,
                    varselUuid = behandlerbrevId!!.second,
                    varselParentId = behandlerbrevParentId,
                    varselInnkallingUuid = behandlerInnkallingUuid,
                )
            }
        }
    }
}
