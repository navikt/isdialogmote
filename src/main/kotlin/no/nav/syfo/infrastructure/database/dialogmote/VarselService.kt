package no.nav.syfo.infrastructure.database.dialogmote

import no.nav.syfo.application.ArbeidstakerVarselService
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.application.NarmesteLederVarselService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.altinn.AltinnClient
import no.nav.syfo.infrastructure.client.altinn.createAltinnMelding
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.isInactive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenClient
import no.nav.syfo.infrastructure.client.arkivporten.ArkivportenDocumentRequestDTO

class VarselService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val behandlerVarselService: BehandlerVarselService,
    private val altinnClient: AltinnClient,
    private val arkivportenClient: ArkivportenClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val isAltinnSendingEnabled: Boolean,
    private val isArkivportenSendingEnabled: Boolean,
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

        if (isArkivportenSendingEnabled) {
            log.info("Arkivporten utsending er aktiv. Starter utsending av $varselType")

            arkivportenClient.sendDocument(
                ArkivportenDocumentRequestDTO.create(
                    reference = virksomhetsbrevId,
                    virksomhetsnummer = virksomhetsnummer,
                    file = virksomhetsPdf,
                    varseltype = varselType,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    arbeidstakernavn = arbeidstakernavn,
                ),
                token = token,
                callId = callId,
            )
        } else {
            log.info("Arkivporten utsending er deaktivert. Dropper utsending av $varselType")
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

    fun sendNarmesteLederSvarVarselHendelse(
        narmesteLederSvar: DialogmoteSvarType,
        narmesteLederPersonIdent: PersonIdent,
        arbeidstakerPersonIdent: PersonIdent,
        virksomhetsnummer: Virksomhetsnummer,
    ) {
        narmesteLederVarselService.sendNarmesteLederSvarVarselHendelse(
            narmesteLederSvar = narmesteLederSvar,
            narmesteLederPersonIdent = narmesteLederPersonIdent,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            virksomhetsnummer = virksomhetsnummer,
        )
    }
}
