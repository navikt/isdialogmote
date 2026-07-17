package no.nav.syfo.application.client

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.motebehov.Tilbakemelding

interface IMotebehovClient {
    suspend fun behandleMotebehov(
        personident: Personident,
        token: String,
    )

    suspend fun sendTilbakemelding(
        tilbakemelding: Tilbakemelding,
        token: String,
    )
}
