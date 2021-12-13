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

    fun opprettVarselSvar(
        arbeidstakerPersonIdent: PersonIdentNumber,
        varseltype: MotedeltakerVarselType,
        svarType: DialogmoteSvarType,
        svarTekst: String?,
        conversationRef: String?,
        parentRef: String?,
        msgId: String,
    ) {
        log.info("Received svar $svarType på varsel $varseltype with conversationRef $conversationRef, parentRef $parentRef and msgId $msgId")
        val pMotedeltakerBehandlerVarsel = getBehandlerVarselForSvar(
            varseltype = varseltype,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            conversationRef = conversationRef,
            parentRef = parentRef,
        )
        pMotedeltakerBehandlerVarsel?.let {
            try {
                log.info("Found varsel for msgId $msgId, conversationRef $conversationRef and parentRef $parentRef")
                database.createMotedeltakerBehandlerVarselSvar(
                    motedeltakerBehandlerVarselId = pMotedeltakerBehandlerVarsel.id,
                    type = svarType,
                    tekst = svarTekst,
                    msgId = msgId,
                )
                log.info("Created svar $svarType på varsel $varseltype with uuid ${pMotedeltakerBehandlerVarsel.uuid}")
                COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS.increment()
            } catch (ex: SQLException) {
                log.error("Could not create svar for varsel", ex)
                COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL.increment()
            }
        }

        log.warn("Could not find varsel for msgId $msgId, conversationRef $conversationRef and parentRef $parentRef - Did not create svar")
    }

    private fun getBehandlerVarselForSvar(
        varseltype: MotedeltakerVarselType,
        arbeidstakerPersonIdent: PersonIdentNumber,
        conversationRef: String?,
        parentRef: String?,
    ): PMotedeltakerBehandlerVarsel? {
        return when (varseltype) {
            MotedeltakerVarselType.INNKALT -> getBehandlerVarselInnkalling(
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                conversationRef = conversationRef,
            )
            MotedeltakerVarselType.NYTT_TID_STED -> getBehandlerVarselNyttTidSted(
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                conversationRef = conversationRef,
                parentRef = parentRef,
            )
            else -> throw IllegalArgumentException("Cannot create svar for varsel $varseltype")
        }
    }

    private fun getBehandlerVarselInnkalling(
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

        return varselInnkallingForConversationRef?.second
            ?: database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstaker(
                varselType = MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent
            )
    }

    private fun getBehandlerVarselNyttTidSted(
        arbeidstakerPersonIdent: PersonIdentNumber,
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
            ?: database.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstaker(
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent
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
