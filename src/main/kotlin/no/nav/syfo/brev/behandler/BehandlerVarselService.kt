package no.nav.syfo.brev.behandler

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.dialogmelding.COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL
import no.nav.syfo.dialogmelding.COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerBehandlerVarsel
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException
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
        varselParentId: String?,
        varselInnkallingUuid: UUID?,
    ) {
        behandlerDialogmeldingProducer.sendDialogmelding(
            dialogmelding = KafkaBehandlerDialogmeldingDTO(
                behandlerRef = behandlerRef,
                personIdent = arbeidstakerPersonIdent.value,
                dialogmeldingUuid = varselUuid.toString(),
                dialogmeldingRefParent = varselParentId,
                dialogmeldingRefConversation = getConversationUuid(varselUuid, varselInnkallingUuid).toString(),
                dialogmeldingType = varseltype.getDialogMeldingType().name,
                dialogmeldingKode = varseltype.getDialogMeldingKode().value,
                dialogmeldingTekst = document.serialize(),
                dialogmeldingVedlegg = pdf,
            )
        )
    }

    fun finnBehandlerVarselOgOpprettSvar(
        arbeidstakerPersonIdent: PersonIdentNumber,
        behandlerPersonIdent: PersonIdentNumber,
        varseltype: MotedeltakerVarselType,
        svarType: DialogmoteSvarType,
        svarTekst: String?,
        conversationRef: String?,
        parentRef: String?,
        msgId: String,
    ): Boolean {
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
        arbeidstakerPersonIdent: PersonIdentNumber,
        behandlerPersonIdent: PersonIdentNumber,
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
        arbeidstakerPersonIdent: PersonIdentNumber,
        behandlerPersonIdent: PersonIdentNumber,
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
        arbeidstakerPersonIdent: PersonIdentNumber,
        behandlerPersonIdent: PersonIdentNumber,
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
        arbeidstakerPersonIdent: PersonIdentNumber,
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
