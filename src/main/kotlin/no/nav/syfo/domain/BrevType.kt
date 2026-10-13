package no.nav.syfo.domain

enum class BrevType {
    AVLYST,
    INNKALT,

    @Deprecated("Use ENDRET")
    NYTT_TID_STED,
    ENDRET,
    REFERAT,
    REFERAT_ENDRET,
}
