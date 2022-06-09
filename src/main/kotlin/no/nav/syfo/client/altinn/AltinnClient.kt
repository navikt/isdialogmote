package no.nav.syfo.client.altinn

import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import org.slf4j.LoggerFactory

class AltinnClient(
    private val altinnSoapClient: ICorrespondenceAgencyExternalBasic,
    private val username: String,
    private val password: String,
) {
    private val SYSTEM_USER_CODE = "NAV_DIGISYFO"
    private val log = LoggerFactory.getLogger(AltinnClient::class.java)

    fun sendToVirksomhet(altinnMelding: AltinnMelding) {
        try {
            val receiptWS = altinnSoapClient.insertCorrespondenceBasicV2(
                username,
                password,
                SYSTEM_USER_CODE,
                altinnMelding.reference.toString(),
                mapToInsertCorrespondenceV2WS(altinnMelding)
            )
            if (receiptWS.receiptStatusCode != ReceiptStatusEnum.OK) {
                log.error(
                    "Error fra altinn {} for virksomhetsbrevId: {}, {}",
                    receiptWS.receiptStatusCode,
                    altinnMelding.reference,
                    receiptWS.receiptText
                )
                COUNT_CALL_ALTINN_MELDINGSTJENESTE_FAIL.increment()
                throw RuntimeException("Error from altinn")
            }
            COUNT_CALL_ALTINN_MELDINGSTJENESTE_SUCCESS.increment()
        } catch (ex: Exception) {
            log.error("Error sending brev to altinn", ex)
            COUNT_CALL_ALTINN_MELDINGSTJENESTE_FAIL.increment()
            throw ex
        }
    }
}
