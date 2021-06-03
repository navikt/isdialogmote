package no.nav.syfo.varsel

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
        MotedeltakerVarselType.AVLYST -> {
            BrevkodeType.DIALOGMOTE_AVLYSNING
        }
        MotedeltakerVarselType.INNKALT -> {
            BrevkodeType.DIALOGMOTE_INNKALLING
        }
        MotedeltakerVarselType.NYTT_TID_STED -> {
            BrevkodeType.DIALOGMOTE_ENDRING_TID_STED
        }
        MotedeltakerVarselType.REFERAT -> {
            BrevkodeType.DIALOGMOTE_REFERAT
        }
    }
}
