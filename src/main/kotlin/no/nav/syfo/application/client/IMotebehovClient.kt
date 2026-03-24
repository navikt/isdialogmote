package no.nav.syfo.application.client

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.motebehov.Tilbakemelding

interface IMotebehovClient {
    suspend fun behandleMotebehov(
        personIdent: PersonIdent,
        token: String,
    )

    suspend fun sendTilbakemelding(
        tilbakemelding: Tilbakemelding,
        token: String,
    )
}
