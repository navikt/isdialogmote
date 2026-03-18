package no.nav.syfo.application

import no.nav.syfo.api.dto.MotebehovTilbakemeldingDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.motebehov.MotebehovClient

class MotebehovService(
    private val motebehovClient: MotebehovClient,
    private val avventRepository: IAvventRepository,
    private val transactionManager: ITransactionManager,
) {
    suspend fun behandleMotebehov(
        personident: PersonIdent,
        tilbakemeldinger: List<MotebehovTilbakemeldingDTO>,
        token: String,
        callId: String,
    ) {
        transactionManager.run { transaction ->
            avventRepository.getActiveAvvent(personident, transaction)?.let {
                avventRepository.setLukket(it.uuid, transaction)
            }
        }
        motebehovClient.behandleMotebehov(
            personIdent = personident,
            token = token,
            callId = callId,
        )
        tilbakemeldinger.forEach { tilbakemelding ->
            motebehovClient.sendTilbakemelding(
                tilbakemelding = tilbakemelding,
                token = token,
                callId = callId,
            )
        }
    }
}
