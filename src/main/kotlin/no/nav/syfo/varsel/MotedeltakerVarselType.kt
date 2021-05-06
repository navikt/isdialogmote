package no.nav.syfo.varsel

enum class MotedeltakerVarselType {
    AVLYST,
    INNKALT,
    NYTT_TID_STED,
}

fun MotedeltakerVarselType.toJournalpostTittel(): String {
    when (this) {
        MotedeltakerVarselType.INNKALT -> {
            return "Innkalling til dialogmøte"
        }
        else -> throw RuntimeException("No JournalpostTittel was found for MotedeltakerVarselType")
    }
}
