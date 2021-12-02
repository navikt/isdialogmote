package no.nav.syfo.dialogmelding

import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.dialogmelding.domain.getDialogmoteSvarType
import no.nav.syfo.dialogmelding.domain.getVarselType
import no.nav.syfo.dialogmelding.kafka.KafkaDialogmeldingDTO
import no.nav.syfo.dialogmelding.kafka.toDialogmeldingSvar
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DialogmeldingService(private val behandlerVarselService: BehandlerVarselService) {

    fun handleDialogmelding(dialogmeldingDTO: KafkaDialogmeldingDTO) {
        if (dialogmeldingDTO.msgType == DIALOGMELDING_TYPE_SVAR) {
            val dialogmeldingSvar = dialogmeldingDTO.toDialogmeldingSvar()
            dialogmeldingSvar.innkallingDialogmoteSvar?.let { innkallingSvar ->
                log.info("Received innkalling dialogmote svar with msgId: ${dialogmeldingDTO.msgId}")
                val varselSvarCreated = behandlerVarselService.opprettVarselSvar(
                    arbeidstakerPersonIdent = dialogmeldingSvar.arbeidstakerPersonIdent,
                    varseltype = innkallingSvar.foresporselType.getVarselType(),
                    svarType = innkallingSvar.svarType.getDialogmoteSvarType(),
                    svarTekst = innkallingSvar.svarTekst,
                    conversationRef = dialogmeldingSvar.conversationRef,
                    parentRef = dialogmeldingSvar.parentRef,
                )
                if (varselSvarCreated) {
                    log.info("Created varsel-svar for innkalling dialogmote svar with msgId: ${dialogmeldingDTO.msgId}")
                    COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS.increment()
                } else {
                    log.error("Failed to create varsel-svar for innkalling dialogmote svar with msgId: ${dialogmeldingDTO.msgId}")
                    COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL.increment()
                }
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DialogmeldingService::class.java)
        private const val DIALOGMELDING_TYPE_SVAR = "DIALOG_SVAR"
    }
}
