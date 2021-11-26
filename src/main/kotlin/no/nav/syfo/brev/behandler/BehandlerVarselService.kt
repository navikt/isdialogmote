package no.nav.syfo.brev.behandler

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.behandler.domain.ForesporselType
import no.nav.syfo.brev.behandler.domain.InnkallingDialogmoteSvar
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger(BehandlerVarselService::class.java)

class BehandlerVarselService(
    private val database: DatabaseInterface,
    private val behandlerDialogmeldingProducer: BehandlerDialogmeldingProducer,
) {
    fun sendVarsel(
        behandlerRef: String,
        arbeidstakerPersonIdent: PersonIdentNumber,
        document: List<DocumentComponentDTO>,
        pdf: ByteArray,
        varseltype: MotedeltakerVarselType,
        varselUuid: UUID,
        varselParentUuid: UUID?,
        varselInnkallingUuid: UUID?,
    ) {
        behandlerDialogmeldingProducer.sendDialogmelding(
            dialogmelding = KafkaBehandlerDialogmeldingDTO(
                behandlerRef = behandlerRef,
                personIdent = arbeidstakerPersonIdent.value,
                dialogmeldingUuid = varselUuid.toString(),
                dialogmeldingRefParent = varselParentUuid?.toString(),
                dialogmeldingRefConversation = getConversationUuid(varselUuid, varselInnkallingUuid).toString(),
                dialogmeldingType = varseltype.getDialogMeldingType().name,
                dialogmeldingKode = varseltype.getDialogMeldingKode().value,
                dialogmeldingTekst = document.serialize(),
                dialogmeldingVedlegg = pdf,
            )
        )
    }

    fun opprettVarselSvar(
        innkallingDialogmoteSvar: InnkallingDialogmoteSvar,
        conversationRef: UUID?,
        parentRef: UUID?,
    ) {
        when (innkallingDialogmoteSvar.foresporselType) {
            ForesporselType.INNKALLING -> {
                log.info("Received dialogmote-svar på INNKALLING with conversationRef $conversationRef and parentRef $parentRef")
                // TODO: Finn innkalling-varsel til behandler i databasen og lagre svar på varselet
            }
            ForesporselType.ENDRING -> {
                log.info("Received dialogmote svar på ENDRING with conversationRef $conversationRef and parentRef $parentRef")
                // TODO: Finn endring-varsel til behandler i databasen og lagre svar på varselet
            }
        }
    }

    private fun getConversationUuid(varselUuid: UUID, varselInnkallingUuid: UUID?): UUID {
        return varselInnkallingUuid ?: varselUuid
    }
}
