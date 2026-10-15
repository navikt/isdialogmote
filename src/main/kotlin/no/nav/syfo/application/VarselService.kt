package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.isInactive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenClient
import no.nav.syfo.infrastructure.client.dokumentporten.DokumentportenDocumentRequestDTO
import no.nav.syfo.infrastructure.client.ereg.EregClient

class VarselService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val behandlerVarselService: BehandlerVarselService,
    private val dokumentportenClient: DokumentportenClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val eregClient: EregClient,
) {
    private val log: Logger = LoggerFactory.getLogger(VarselService::class.java)

    suspend fun sendVarsel(
        varselType: MotedeltakerVarselType,
        isDigitalVarselEnabledForArbeidstaker: Boolean,
        arbeidstakerPersonIdent: Personident,
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
        val tilfelle = oppfolgingstilfelleClient.oppfolgingstilfellePerson(
            callId = callId,
            personident = arbeidstakerPersonIdent,
            token = token,
        )

        val hasActiveTilfelle = tilfelle != null && !tilfelle.isInactive()

        log.info("Starter utsending av $varselType til Dokumentporten")
        val manglerNarmesteLeder = narmesteLeder == null
        val virksomhetsnavn =
            if (manglerNarmesteLeder) eregClient.organisasjonVirksomhetsnavn(virksomhetsnummer) else null

        dokumentportenClient.sendDocument(
            DokumentportenDocumentRequestDTO.create(
                reference = virksomhetsbrevId,
                virksomhetsnummer = virksomhetsnummer,
                virksomhetsnavn = virksomhetsnavn,
                file = virksomhetsPdf,
                varseltype = varselType,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                arbeidstakernavn = arbeidstakernavn,
                manglerNarmesteLeder = manglerNarmesteLeder,
            ),
            token = token,
            callId = callId,
        )

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
                personident = arbeidstakerPersonIdent,
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
                    token = token,
                    callId = callId,
                )
            }
        }
    }

    suspend fun sendBehandlerVarsel(
        behandlerRef: String,
        arbeidstakerPersonIdent: Personident,
        document: List<DocumentComponentDTO>,
        pdf: ByteArray,
        varselType: MotedeltakerVarselType,
        varselUuid: UUID,
        varselParentId: String?,
        varselInnkallingUuid: UUID?,
        token: String,
        callId: String,
    ) = behandlerVarselService.sendVarsel(
        behandlerRef = behandlerRef,
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        document = document,
        pdf = pdf,
        varseltype = varselType,
        varselUuid = varselUuid,
        varselParentId = varselParentId,
        varselInnkallingUuid = varselInnkallingUuid,
        token = token,
        callId = callId,
    )

    fun sendNarmesteLederSvarVarselHendelse(
        narmesteLederSvar: DialogmoteSvarType,
        narmesteLederPersonIdent: Personident,
        arbeidstakerPersonIdent: Personident,
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
