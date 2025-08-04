package no.nav.syfo.domain

// TODO: bytt fra respons til svar for å være konsekvent
data class ArbeidstakerResponsDTO(
    val svarType: String,
    val svarTekst: String?,
)
