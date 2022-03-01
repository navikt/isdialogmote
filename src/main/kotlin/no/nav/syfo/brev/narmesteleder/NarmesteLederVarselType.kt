package no.nav.syfo.brev.narmesteleder

enum class NarmesteLederVarselType(val id: String) {
    NARMESTE_LEDER_MOTE_INNKALT("syfoDialogmoteInnkalt"),
    NARMESTE_LEDER_MOTE_AVLYST("syfoDialogmoteAvlyst"),
    NARMESTE_LEDER_MOTE_NYTID("syfoDialogmoteNytid"),
    NARMESTE_LEDER_REFERAT("syfoDialogmoteReferat"),
}

enum class DineSykmeldteOppgavetype {
    DIALOGMOTE_INNKALLING,
    DIALOGMOTE_AVLYSNING,
    DIALOGMOTE_ENDRING,
    DIALOGMOTE_REFERAT,
}
