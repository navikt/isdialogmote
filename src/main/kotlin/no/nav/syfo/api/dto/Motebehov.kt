package no.nav.syfo.api.dto

data class MotebehovTilbakemeldingDTO(
    val varseltekst: String,
    val motebehovId: String,
)

data class BehandleMotebehovDTO(
    val personident: String,
    val motebehovTilbakemelding: MotebehovTilbakemeldingDTO?,
)
