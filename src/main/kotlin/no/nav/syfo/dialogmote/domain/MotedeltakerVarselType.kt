package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType

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

fun MotedeltakerVarselType.toBrevkodeType(): BrevkodeType {
    return when (this) {
        MotedeltakerVarselType.AVLYST -> BrevkodeType.DIALOGMOTE_AVLYSNING
        MotedeltakerVarselType.INNKALT -> BrevkodeType.DIALOGMOTE_INNKALLING
        MotedeltakerVarselType.NYTT_TID_STED -> BrevkodeType.DIALOGMOTE_ENDRING_TID_STED
        MotedeltakerVarselType.REFERAT -> BrevkodeType.DIALOGMOTE_REFERAT
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
