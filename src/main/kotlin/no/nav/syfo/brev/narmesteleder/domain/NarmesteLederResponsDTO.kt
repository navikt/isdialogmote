package no.nav.syfo.brev.narmesteleder.domain

// TODO: bytt fra respons til svar for å være konsekvent
data class NarmesteLederResponsDTO(
    val svarType: String,
    val svarTekst: String?,
)
