package no.nav.syfo.dialogmelding

import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.dialogmelding.domain.getDialogmoteSvarType
import no.nav.syfo.dialogmelding.domain.getVarselType
import no.nav.syfo.dialogmelding.kafka.KafkaDialogmeldingDTO
import no.nav.syfo.dialogmelding.kafka.toDialogmeldingSvarAlternativer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DialogmeldingService(private val behandlerVarselService: BehandlerVarselService) {

    fun handleDialogmelding(dialogmeldingDTO: KafkaDialogmeldingDTO) {
        if (dialogmeldingDTO.msgType == DIALOGMELDING_TYPE_SVAR) {
            dialogmeldingDTO.toDialogmeldingSvarAlternativer().find { dialogmeldingSvar ->
                log.info("Received innkalling dialogmote svar with msgId: ${dialogmeldingDTO.msgId}")
                behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
                    arbeidstakerPersonIdent = dialogmeldingSvar.arbeidstakerPersonIdent,
                    behandlerPersonIdent = dialogmeldingSvar.behandlerPersonIdent,
                    varseltype = dialogmeldingSvar.innkallingDialogmoteSvar.foresporselType.getVarselType(),
                    svarType = dialogmeldingSvar.innkallingDialogmoteSvar.svarType.getDialogmoteSvarType(),
                    svarTekst = dialogmeldingSvar.innkallingDialogmoteSvar.svarTekst,
                    conversationRef = dialogmeldingSvar.conversationRef,
                    parentRef = dialogmeldingSvar.parentRef,
                    msgId = dialogmeldingDTO.msgId,
                )
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DialogmeldingService::class.java)
        private const val DIALOGMELDING_TYPE_SVAR = "DIALOG_SVAR"
    }
}
