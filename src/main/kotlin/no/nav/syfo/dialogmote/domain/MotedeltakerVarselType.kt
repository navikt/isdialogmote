package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.DialogmoteDeltakerType

enum class MotedeltakerVarselType {
    AVLYST,
    INNKALT,
    NYTT_TID_STED,
    REFERAT,
}

fun MotedeltakerVarselType.toJournalpostTittel(): String {
    return when (this) {
        MotedeltakerVarselType.AVLYST -> {
            "Avlysning av innkalt dialogmøte"
        }
        MotedeltakerVarselType.INNKALT -> {
            "Innkalling til dialogmøte"
        }
        MotedeltakerVarselType.NYTT_TID_STED -> {
            "Endring av tid og sted for innkalt dialogmøte"
        }
        MotedeltakerVarselType.REFERAT -> {
            "Referat fra dialogmøte"
        }
    }
}

fun MotedeltakerVarselType.toBrevkodeType(
    dialogmoteDeltakerType: DialogmoteDeltakerType,
): BrevkodeType {
    return when (this) {
        MotedeltakerVarselType.AVLYST ->
            when (dialogmoteDeltakerType) {
                DialogmoteDeltakerType.ARBEIDSTAKER -> BrevkodeType.DIALOGMOTE_AVLYSNING_AT
                DialogmoteDeltakerType.ARBEIDSGIVER -> BrevkodeType.DIALOGMOTE_AVLYSNING_AG
                DialogmoteDeltakerType.BEHANDLER -> BrevkodeType.DIALOGMOTE_AVLYSNING_BEH
            }
        MotedeltakerVarselType.INNKALT ->
            when (dialogmoteDeltakerType) {
                DialogmoteDeltakerType.ARBEIDSTAKER -> BrevkodeType.DIALOGMOTE_INNKALLING_AT
                DialogmoteDeltakerType.ARBEIDSGIVER -> BrevkodeType.DIALOGMOTE_INNKALLING_AG
                DialogmoteDeltakerType.BEHANDLER -> BrevkodeType.DIALOGMOTE_INNKALLING_BEH
            }
        MotedeltakerVarselType.NYTT_TID_STED ->
            when (dialogmoteDeltakerType) {
                DialogmoteDeltakerType.ARBEIDSTAKER -> BrevkodeType.DIALOGMOTE_ENDRING_TID_STED_AT
                DialogmoteDeltakerType.ARBEIDSGIVER -> BrevkodeType.DIALOGMOTE_ENDRING_TID_STED_AG
                DialogmoteDeltakerType.BEHANDLER -> BrevkodeType.DIALOGMOTE_ENDRING_TID_STED_BEH
            }
        MotedeltakerVarselType.REFERAT ->
            when (dialogmoteDeltakerType) {
                DialogmoteDeltakerType.ARBEIDSTAKER -> BrevkodeType.DIALOGMOTE_REFERAT_AT
                DialogmoteDeltakerType.ARBEIDSGIVER -> BrevkodeType.DIALOGMOTE_REFERAT_AG
                DialogmoteDeltakerType.BEHANDLER -> BrevkodeType.DIALOGMOTE_REFERAT_BEH
            }
    }
}

fun MotedeltakerVarselType.getDialogMeldingType(): DialogmeldingType {
    return when (this) {
        MotedeltakerVarselType.INNKALT -> DialogmeldingType.DIALOG_FORESPORSEL
        MotedeltakerVarselType.NYTT_TID_STED -> DialogmeldingType.DIALOG_FORESPORSEL
        MotedeltakerVarselType.AVLYST -> DialogmeldingType.DIALOG_NOTAT
        MotedeltakerVarselType.REFERAT -> DialogmeldingType.DIALOG_NOTAT
    }
}

fun MotedeltakerVarselType.getDialogMeldingKode(): DialogmeldingKode {
    return when (this) {
        MotedeltakerVarselType.INNKALT -> DialogmeldingKode.INNKALLING
        MotedeltakerVarselType.NYTT_TID_STED -> DialogmeldingKode.TIDSTED
        MotedeltakerVarselType.AVLYST -> DialogmeldingKode.AVLYST
        MotedeltakerVarselType.REFERAT -> DialogmeldingKode.REFERAT
    }
}

enum class DialogmeldingKode(
    val value: Int
) {
    INNKALLING(1),
    TIDSTED(2),
    AVLYST(4),
    REFERAT(9),
}

enum class DialogmeldingType {
    DIALOG_FORESPORSEL,
    DIALOG_NOTAT,
}
