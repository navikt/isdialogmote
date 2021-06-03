package no.nav.syfo.varsel.narmesteleder

enum class NarmesteLederVarselType(val id: String) {
    NARMESTE_LEDER_MOTE_PLANLAGT("syfoDialogmotePlanlagt"),
    NARMESTE_LEDER_MOTE_AVLYST("syfoDialogmoteAvlyst"),
    NARMESTE_LEDER_MOTE_NYTID("syfoDialogmoteNytid"),
    NARMESTE_LEDER_REFERAT("syfoDialogmoteReferat"),
}
