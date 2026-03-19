package no.nav.syfo.api.dto

import no.nav.syfo.domain.motebehov.Tilbakemelding

data class MotebehovTilbakemeldingDTO(
    val varseltekst: String,
    val motebehovId: String,
) {
    fun toTilbakemelding(): Tilbakemelding =
        Tilbakemelding(
            varseltekst = this.varseltekst,
            motebehovId = this.motebehovId,
        )
}

data class BehandleMotebehovDTO(
    val personident: String,
    val tilbakemeldinger: List<MotebehovTilbakemeldingDTO>,
)
