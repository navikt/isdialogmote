package no.nav.syfo.brev.behandler

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.dialogmote.database.createMotedeltakerBehandlerVarselSvar
import no.nav.syfo.dialogmote.database.getMotedeltakerBehandlerVarselForUuid
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
        varseltype: MotedeltakerVarselType,
        svarType: DialogmoteSvarType,
        svarTekst: String?,
        conversationRef: UUID?,
        parentRef: UUID?
    ) {
        log.info("Received svar $svarType på varsel $varseltype with conversationRef $conversationRef and parentRef $parentRef")
        when (varseltype) {
            MotedeltakerVarselType.INNKALT -> opprettInnkallingVarselSvar(
                type = svarType,
                tekst = svarTekst,
                conversationRef = conversationRef
            )
            MotedeltakerVarselType.NYTT_TID_STED -> opprettEndringVarselSvar(
                type = svarType,
                tekst = svarTekst,
                conversationRef = conversationRef,
                parentRef = parentRef
            )
            else -> throw IllegalArgumentException("Could not create svar for varsel $varseltype")
        }
    }

    private fun opprettInnkallingVarselSvar(
        type: DialogmoteSvarType,
        tekst: String?,
        conversationRef: UUID?
    ) {
        val pMotedeltakerBehandlerVarsel =
            conversationRef?.let { database.getMotedeltakerBehandlerVarselForUuid(it) }
        if (pMotedeltakerBehandlerVarsel?.varselType == MotedeltakerVarselType.INNKALT) {
            database.createMotedeltakerBehandlerVarselSvar(
                motedeltakerBehandlerVarselId = pMotedeltakerBehandlerVarsel.id,
                type = type,
                tekst = tekst,
            )
        } else {
            log.warn("Could not find MotedeltakerBehandlerVarsel of type ${MotedeltakerVarselType.INNKALT.name} for dialogmote-svar på INNKALLING")
        }
    }

    private fun opprettEndringVarselSvar(
        type: DialogmoteSvarType,
        tekst: String?,
        conversationRef: UUID?,
        parentRef: UUID?
    ) {
        // TODO: Finn endring-varsel til behandler i databasen og lagre svar på varselet
    }

    private fun getConversationUuid(varselUuid: UUID, varselInnkallingUuid: UUID?): UUID {
        return varselInnkallingUuid ?: varselUuid
    }
}
