package no.nav.syfo.application

import no.nav.syfo.application.client.IMotebehovClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.motebehov.Tilbakemelding

class MotebehovService(
    private val motebehovClient: IMotebehovClient,
    private val avventRepository: IAvventRepository,
    private val transactionManager: ITransactionManager,
) {
    suspend fun behandleMotebehov(
        personident: PersonIdent,
        harBehovForMote: Boolean,
        tilbakemeldinger: List<Tilbakemelding>,
        token: String,
        callId: String,
    ) {
        motebehovClient.behandleMotebehov(
            personIdent = personident,
            token = token,
            callId = callId,
        )
        if (!harBehovForMote) {
            transactionManager.run { transaction ->
                avventRepository.getActiveAvvent(personident, transaction)?.let {
                    avventRepository.setLukket(it.uuid, transaction)
                }
            }
        }
        tilbakemeldinger.forEach { tilbakemelding ->
            motebehovClient.sendTilbakemelding(
                tilbakemelding = tilbakemelding,
                token = token,
                callId = callId,
            )
        }
    }
}
