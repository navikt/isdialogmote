package no.nav.syfo.dialogmelding

import no.nav.syfo.brev.behandler.BehandlerVarselService
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
                    dialogmeldingSvar = dialogmeldingSvar,
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
