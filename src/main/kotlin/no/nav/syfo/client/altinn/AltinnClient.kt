package no.nav.syfo.client.altinn

import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.InsertCorrespondenceV2
import no.nav.syfo.domain.Virksomhetsnummer
import org.slf4j.LoggerFactory
import java.util.*

class AltinnClient(
    altinnWsUrl: String,
    private val username: String,
    private val password: String
) {
    private val SYSTEM_USER_CODE = "NAV_DIGISYFO"
    private val log = LoggerFactory.getLogger(AltinnClient::class.java)
    private val iCorrespondenceAgencyExternalBasic = createPort(altinnWsUrl)

    fun sendToVirksomhet(
        brevUuid: UUID,
        brevPdf: ByteArray,
        virksomhetsnummer: Virksomhetsnummer,
    ) {
        val virksomhetsBrevAltinnWSRequest = createVirksomhetsBrevAltinnWSRequest(
            brevUuid = brevUuid,
            brev = brevPdf,
            virksomhetsnummer = virksomhetsnummer
        )
        sendMelding(virksomhetsBrevAltinnWSRequest, brevUuid)
    }

    private fun sendMelding(virksomhetsBrevAltinnWS: InsertCorrespondenceV2, brevId: UUID): Int {
        try {
            val receiptWS = iCorrespondenceAgencyExternalBasic.insertCorrespondenceBasicV2(
                username,
                password,
                SYSTEM_USER_CODE,
                brevId.toString(),
                virksomhetsBrevAltinnWS
            )
            if (receiptWS.receiptStatusCode != ReceiptStatusEnum.OK) {
                log.error(
                    "Error fra altinn {} for virksomhetsbrevId: {}, {}",
                    receiptWS.receiptStatusCode,
                    brevId,
                    receiptWS.receiptText
                )
                throw RuntimeException("Error from altinn")
            }
            return receiptWS.receiptId
        } catch (ex: Exception) {
            log.error("Error sending brev to altinn", ex)
            throw ex
        }
    }
}
