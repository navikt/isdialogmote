package no.nav.syfo.dialogmote.domain

enum class DialogmoteStatus {
    INNKALT,
    AVLYST,
    FERDIGSTILT,
    NYTT_TID_STED,
    LUKKET,
}

fun DialogmoteStatus.unfinished() = this == DialogmoteStatus.INNKALT || this == DialogmoteStatus.NYTT_TID_STED
