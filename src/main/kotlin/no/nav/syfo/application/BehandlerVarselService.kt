package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.kafka.behandler.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.domain.DialogmeldingSvar
import no.nav.syfo.domain.getDialogmoteSvarType
import no.nav.syfo.domain.getVarselType
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarsel
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.domain.dialogmote.getDialogMeldingKode
import no.nav.syfo.domain.dialogmote.getDialogMeldingKodeverk
import no.nav.syfo.domain.dialogmote.getDialogMeldingType
import no.nav.syfo.domain.dialogmote.serialize
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.database.createMotedeltakerBehandlerVarselSvar
import no.nav.syfo.infrastructure.database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler
import no.nav.syfo.infrastructure.database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndMoteId
import no.nav.syfo.infrastructure.database.getMote
import no.nav.syfo.infrastructure.database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

private val log: Logger = LoggerFactory.getLogger(BehandlerVarselService::class.java)

class BehandlerVarselService(
    private val database: DatabaseInterface,
    private val dialogmeldingClient: DialogmeldingClient,
    private val behandlerDialogmeldingProducer: BehandlerDialogmeldingProducer,
) {
    suspend fun sendVarsel(
        behandlerRef: String,
        arbeidstakerPersonIdent: Personident,
        document: List<DocumentComponentDTO>,
        pdf: ByteArray,
        varseltype: MotedeltakerVarselType,
        varselUuid: UUID,
        varselParentId: String?,
        varselInnkallingUuid: UUID?,
        token: String,
        callId: String,
    ) {
        val behandlerRefToUse = finnBehandlerRefTilUtsending(
            behandlerRef = behandlerRef,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            token = token,
            callId = callId,
        )

        behandlerDialogmeldingProducer.sendDialogmelding(
            dialogmelding = KafkaBehandlerDialogmeldingDTO(
                behandlerRef = behandlerRefToUse,
                personIdent = arbeidstakerPersonIdent.value,
                dialogmeldingUuid = varselUuid.toString(),
                dialogmeldingRefParent = varselParentId,
                dialogmeldingRefConversation = getConversationUuid(varselUuid, varselInnkallingUuid).toString(),
                dialogmeldingType = varseltype.getDialogMeldingType().name,
                dialogmeldingKodeverk = varseltype.getDialogMeldingKodeverk().name,
                dialogmeldingKode = varseltype.getDialogMeldingKode().value,
                dialogmeldingTekst = document.serialize(),
                dialogmeldingVedlegg = pdf,
                kilde = "SYFO",
            )
        )
    }

    private suspend fun finnBehandlerRefTilUtsending(
        behandlerRef: String,
        arbeidstakerPersonIdent: Personident,
        token: String,
        callId: String,
    ): String {
        val behandlerDTO = dialogmeldingClient.getBehandler(UUID.fromString(behandlerRef))
            ?: throw RuntimeException("Failed to send varsel: Could not find behandler with behandlerRef $behandlerRef")

        val behandlerKanMottaDialogmelding = !behandlerDTO.invalidated && behandlerDTO.kontorDialogmeldingmeldingEnabled
        return if (behandlerKanMottaDialogmelding) {
            log.info("Existing behandler is ok")
            behandlerRef
        } else {
            val behandlerDTOList = dialogmeldingClient.getBehandlereForPerson(
                personident = arbeidstakerPersonIdent,
                token = token,
                callId = callId,
            )
            val erstatningsbehandler = behandlerDTOList.firstOrNull {
                it.kontorDialogmeldingmeldingEnabled && it.hprId != null && it.hprId == behandlerDTO.hprId
            }
            if (erstatningsbehandler != null) {
                log.warn("Behandler with behandlerRef $behandlerRef cannot receive dialogmelding. Using erstatningsbehandler ${erstatningsbehandler.behandlerRef} instead")
            } else {
                log.error("Behandler with behandlerRef $behandlerRef cannot receive dialogmelding but found no replacement")
            }
            erstatningsbehandler?.behandlerRef ?: behandlerRef
        }
    }

    fun finnBehandlerVarselOgOpprettSvar(
        dialogmeldingSvar: DialogmeldingSvar,
        msgId: String,
    ): Boolean {
        val arbeidstakerPersonIdent = dialogmeldingSvar.arbeidstakerPersonIdent
        val behandlerPersonIdent = dialogmeldingSvar.behandlerPersonIdent
        val varseltype = dialogmeldingSvar.innkallingDialogmoteSvar.foresporselType.getVarselType()
        val svarType = dialogmeldingSvar.innkallingDialogmoteSvar.svarType.getDialogmoteSvarType()
        val svarTekst = dialogmeldingSvar.innkallingDialogmoteSvar.svarTekst
        val conversationRef = dialogmeldingSvar.conversationRef
        val parentRef = dialogmeldingSvar.parentRef

        log.info("Received svar $svarType på varsel $varseltype with conversationRef $conversationRef, parentRef $parentRef and msgId $msgId")
        val pMotedeltakerBehandlerVarsel = getBehandlerVarselForSvar(
            varseltype = varseltype,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            behandlerPersonIdent = behandlerPersonIdent,
            conversationRef = conversationRef,
            parentRef = parentRef,
        )
        return if (pMotedeltakerBehandlerVarsel != null) {
            try {
                log.info("Found varsel with uuid ${pMotedeltakerBehandlerVarsel.uuid}")
                val currentMote = database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel)
                if (currentMote == null) {
                    log.error("Could not find mote for behandlerVarsel ${pMotedeltakerBehandlerVarsel.uuid} conversationRef $conversationRef, parentRef $parentRef and msgId $msgId - Did not create svar")
                    return false
                }
                database.createMotedeltakerBehandlerVarselSvar(
                    motedeltakerBehandlerVarselId = pMotedeltakerBehandlerVarsel.id,
                    type = svarType,
                    tekst = svarTekst,
                    msgId = msgId,
                )
                log.info("Created svar $svarType på varsel $varseltype with uuid ${pMotedeltakerBehandlerVarsel.uuid}")
                COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS.increment()
                true
            } catch (ex: SQLException) {
                log.error("Could not create svar for varsel", ex)
                COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL.increment()
                false
            }
        } else {
            log.warn("Could not find varsel for conversationRef $conversationRef, parentRef $parentRef and msgId $msgId - Did not create svar")
            false
        }
    }

    private fun getBehandlerVarselForSvar(
        varseltype: MotedeltakerVarselType,
        arbeidstakerPersonIdent: Personident,
        behandlerPersonIdent: Personident,
        conversationRef: String?,
        parentRef: String?,
    ): PMotedeltakerBehandlerVarsel? {
        return when (varseltype) {
            MotedeltakerVarselType.INNKALT -> getBehandlerVarselInnkalling(
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                behandlerPersonIdent = behandlerPersonIdent,
                conversationRef = conversationRef,
            )
            MotedeltakerVarselType.NYTT_TID_STED -> getBehandlerVarselNyttTidSted(
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                behandlerPersonIdent = behandlerPersonIdent,
                conversationRef = conversationRef,
                parentRef = parentRef,
            )
            else -> throw IllegalArgumentException("Cannot create svar for varsel $varseltype")
        }
    }

    private fun getBehandlerVarselInnkalling(
        arbeidstakerPersonIdent: Personident,
        behandlerPersonIdent: Personident,
        conversationRef: String?,
    ): PMotedeltakerBehandlerVarsel? {
        val varselInnkallingForConversationRef =
            conversationRef?.let {
                database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                    varselType = MotedeltakerVarselType.INNKALT,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    uuid = it
                )
            }

        return varselInnkallingForConversationRef?.second
            ?: database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler(
                varselType = MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                behandlerPersonIdent = behandlerPersonIdent,
            )
    }

    private fun getBehandlerVarselNyttTidSted(
        arbeidstakerPersonIdent: Personident,
        behandlerPersonIdent: Personident,
        conversationRef: String?,
        parentRef: String?,
    ): PMotedeltakerBehandlerVarsel? {
        val varselNyttTidStedForParentRef =
            parentRef?.let {
                database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                    varselType = MotedeltakerVarselType.NYTT_TID_STED,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    uuid = it
                )
            }

        if (varselNyttTidStedForParentRef != null) {
            return varselNyttTidStedForParentRef.second
        }

        val varselNyttTidStedForConversationRef = getBehandlerVarselNyttTidStedFromConversationRef(
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            conversationRef = conversationRef
        )
        return varselNyttTidStedForConversationRef
            ?: database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler(
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                behandlerPersonIdent = behandlerPersonIdent,
            )
    }

    private fun getBehandlerVarselNyttTidStedFromConversationRef(
        arbeidstakerPersonIdent: Personident,
        conversationRef: String?,
    ): PMotedeltakerBehandlerVarsel? {
        val varselInnkallingForConversationRef =
            conversationRef?.let {
                database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                    varselType = MotedeltakerVarselType.INNKALT,
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    uuid = it
                )
            }

        return varselInnkallingForConversationRef?.first?.let {
            database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndMoteId(
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                moteId = it
            )
        }
    }

    private fun getConversationUuid(varselUuid: UUID, varselInnkallingUuid: UUID?): UUID {
        return varselInnkallingUuid ?: varselUuid
    }
}
